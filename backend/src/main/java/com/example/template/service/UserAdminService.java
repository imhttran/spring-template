package com.example.template.service;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.example.template.repository.UserRepository;
import com.example.template.service.EmailQueueService.ResetKey;
import com.example.template.service.error.ConflictException;
import com.example.template.service.error.NotFoundException;
import com.example.template.service.error.ServerErrorException;
import com.example.template.service.error.ValidationException;

/**
 * Staff/admin user management.
 *
 * <p>The role gate itself (staff-or-higher, admin-only) is applied by the
 * controller before anything here runs, because the original checks the role
 * before it parses the path id — a client asking to delete
 * {@code /api/users/abc} gets 403, not 400. Keeping that order visible in one
 * place beats spreading it across layers.
 */
@Service
public class UserAdminService {

    private final UserRepository users;
    private final EmailQueueService queuedEmails;
    private final PasswordHasher hasher;

    public UserAdminService(UserRepository users, EmailQueueService queuedEmails, PasswordHasher hasher) {
        this.users = users;
        this.queuedEmails = queuedEmails;
        this.hasher = hasher;
    }

    /** Staff see clients and other staff; admin sees everyone. */
    public List<UserRepository.ListItem> listUsers(boolean includeAdminAccounts) {
        try {
            return users.list(includeAdminAccounts);
        } catch (DataAccessException failed) {
            throw new ServerErrorException("List Users Error", failed, false);
        }
    }

    /**
     * Admin-only: creates a user with an admin-chosen password, already
     * verified (the admin vouches for the email) and flagged to force a
     * password change on first login.
     */
    public UserRepository.UserWithRole createUser(String email, String password) {
        if (!Validators.isEmail(email)) {
            throw new ValidationException("Invalid email address");
        }
        String passwordError = Validators.validatePassword(password);
        if (passwordError != null) {
            throw new ValidationException(passwordError);
        }
        try {
            return users.insertAdminCreatedUser(email, hasher.hash(password));
        } catch (DuplicateKeyException alreadyRegistered) {
            throw new ConflictException("Email is already registered");
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Admin Create User Error", failed, true);
        }
    }

    /** Staff can nudge a not-yet-verified user's verification email along. */
    public void resendVerification(int id) {
        UserRepository.VerificationRow row;
        try {
            row = users.findVerificationRowById(id).orElse(null);
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Resend Verification Error", failed, false);
        }
        if (row == null) {
            throw new NotFoundException("User not found");
        }
        if (row.emailVerified()) {
            throw new ValidationException("User is already verified");
        }
        try {
            queuedEmails.queueVerificationEmail(row.id(), row.email());
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Resend Verification Error", failed, false);
        }
    }

    /** Admin-only: flip a user's verified flag directly, no email round-trip. */
    public UserRepository.VerificationRow setVerification(int id, boolean verified) {
        try {
            return users.updateVerification(id, verified)
                    .orElseThrow(() -> new NotFoundException("User not found"));
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Update Verification Error", failed, false);
        }
    }

    /**
     * Admin-only: changes a user's role. Blocks self-demotion so an admin can't
     * lock themselves (and potentially every other admin) out of admin routes.
     */
    public UserRepository.RoleRow setRole(int requesterId, int id, String role) {
        if (!Roles.isRole(role)) {
            throw new ValidationException("role must be one of: " + String.join(", ", Roles.ROLES));
        }
        if (id == requesterId) {
            throw new ValidationException("Cannot change your own role");
        }
        try {
            return users.updateRole(id, role).orElseThrow(() -> new NotFoundException("User not found"));
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Update Role Error", failed, false);
        }
    }

    /**
     * Admin-only: sends the same reset-password email a user would trigger
     * themselves, so an admin never has to see or set anyone's password.
     */
    public void resetPassword(int id) {
        try {
            queuedEmails.queuePasswordReset(new ResetKey.ById(id));
        } catch (NotFoundException noSuchUser) {
            throw new NotFoundException("User not found");
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Admin Reset Password Error", failed, false);
        }
    }

    public void deleteUser(int requesterId, int id) {
        if (id == requesterId) {
            throw new ValidationException("Cannot delete your own account");
        }
        try {
            if (users.deleteById(id) == 0) {
                throw new NotFoundException("User not found");
            }
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Delete User Error", failed, false);
        }
    }
}

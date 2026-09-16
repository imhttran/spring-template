package com.example.template.api;

import com.example.template.service.AuthService;
import com.example.template.service.AuthUser;
import com.example.template.service.error.ForbiddenException;
import com.example.template.service.error.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves a logged-in user for any handler that declares an {@code AuthUser}
 * parameter. Declaring the parameter IS the auth check (the port of
 * {@code requireAuth}) — token verification, user lookup, the verification
 * flag and the onboarding gates all run here, and public routes simply don't
 * ask for one.
 */
@Component
public class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * A user working through one onboarding gate can still reach the other
     * gate's route, so these are exempt from every gate (not just their own).
     * The frontend redirect isn't the only thing stopping a temp password or an
     * empty profile from driving the API.
     */
    private static final Set<String> ONBOARDING_EXEMPT_ROUTES = Set.of(
        "GET /api/me",
        "POST /api/change-password",
        "GET /api/profile",
        "POST /api/profile"
    );

    private final AuthService auth;

    public AuthUserArgumentResolver(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = webRequest.getNativeRequest(
            HttpServletRequest.class
        );
        String token = BearerToken.from(request);
        if (token.isEmpty()) {
            throw new ApiRejection(
                HttpStatus.UNAUTHORIZED,
                Api.msg("No token provided")
            );
        }
        AuthUser user = authenticate(token);

        String route = request.getMethod() + " " + request.getRequestURI();
        if (!ONBOARDING_EXEMPT_ROUTES.contains(route)) {
            // A logged-in user can be mid-onboarding — temp password not yet
            // changed, registration details not yet filled in, possibly both at
            // once. The gates only need to know a profile exists, not its
            // contents.
            if (user.mustChangePassword()) {
                throw new ApiRejection(
                    HttpStatus.FORBIDDEN,
                    Api.msg("Password change required")
                );
            }
            if (!user.hasProfile()) {
                throw new ApiRejection(
                    HttpStatus.FORBIDDEN,
                    Api.msg("Profile information required")
                );
            }
        }
        return user;
    }

    /**
     * Service exceptions are re-shaped here: every gate response is the bare
     * {@code {"message": …}} form, while the same exception types mean
     * something else on the login routes.
     */
    private AuthUser authenticate(String token) {
        try {
            return auth.authenticate(token);
        } catch (ForbiddenException rejected) {
            throw new ApiRejection(
                HttpStatus.FORBIDDEN,
                Api.msg(rejected.getMessage())
            );
        } catch (NotFoundException missing) {
            throw new ApiRejection(
                HttpStatus.NOT_FOUND,
                Api.msg(missing.getMessage())
            );
        }
    }
}

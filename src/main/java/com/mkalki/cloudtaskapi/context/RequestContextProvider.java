package com.mkalki.cloudtaskapi.context;

import com.mkalki.cloudtaskapi.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class RequestContextProvider {

    private final HttpServletRequest request;

    public  RequestContextProvider(HttpServletRequest request) {
        this.request = request;
    }

    public RequestContext getCurrentContext() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if(authentication == null ||
                !authentication.isAuthenticated()||
                !(authentication.getPrincipal() instanceof User)
        ) {
            throw new IllegalStateException("No authenticated user found");
        }

        User user = (User) authentication.getPrincipal();

        Long userId = user.getId();

        String ipAddress = request.getRemoteAddr();

        String userAgent = request.getHeader("User-Agent");

        return new RequestContext(
                userId,
                ipAddress,
                userAgent
        );
    }

    public RequestContext getContextForUser(Long userId) {

        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        return new RequestContext(
                userId,
                ipAddress,
                userAgent
        );
    }
}

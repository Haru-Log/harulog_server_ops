package goojeans.harulog.user.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import goojeans.harulog.domain.BusinessException;
import goojeans.harulog.domain.ResponseCode;
import goojeans.harulog.domain.dto.Response;
import goojeans.harulog.user.domain.dto.CustomOAuth2User;
import goojeans.harulog.user.domain.entity.Users;
import goojeans.harulog.user.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Value("${jwt.cookie.expiration}")
    private Integer COOKIE_EXPIRATION;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        try{

            CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

            Authentication authentication1 = jwtTokenProvider.createAuthentication(oAuth2User.getUser());

            String accessToken = jwtTokenProvider.generateAccessToken(authentication1);
            response.addHeader("Authorization", accessToken);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");

            try {
                String responseBody = objectMapper.writeValueAsString(Response.ok());
                response.getWriter().write(responseBody);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            String refreshToken = jwtTokenProvider.generateRefreshToken();
            Cookie cookie = new Cookie("refreshToken", refreshToken);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(COOKIE_EXPIRATION);
            response.addCookie(cookie);

            Users findUser = userRepository.findUsersByEmail(oAuth2User.getUser().getEmail())
                    .orElseThrow(() -> new BusinessException(ResponseCode.USER_NOT_FOUND));

            // User 의 Role 이 GUEST 일 경우 처음 요청한 회원이므로 회원가입 페이지로 리다이렉트
            if(oAuth2User.getUser().getUserRole() == UserRole.GUEST) {
                findUser.updateUserRole(UserRole.USER);
                //TODO: redirect 추가
//            response.sendRedirect("oauth2/sign-up");
            }

        } catch (Exception e) {
            throw new BusinessException(ResponseCode.USER_UNAUTHORIZED);
        }
    }
}
package goojeans.harulog.user.service;

import goojeans.harulog.category.domain.entity.Category;
import goojeans.harulog.category.repository.CategoryRepository;
import goojeans.harulog.domain.BusinessException;
import goojeans.harulog.domain.ResponseCode;
import goojeans.harulog.domain.dto.Response;
import goojeans.harulog.user.domain.dto.JwtUserDetail;
import goojeans.harulog.user.domain.dto.request.DeleteUserRequest;
import goojeans.harulog.user.domain.dto.request.SignUpRequest;
import goojeans.harulog.user.domain.dto.request.UpdatePasswordRequest;
import goojeans.harulog.user.domain.dto.request.UpdateUserInfoRequest;
import goojeans.harulog.user.domain.dto.response.MyPageInfoResponse;
import goojeans.harulog.user.domain.dto.response.UserInfoEditResponse;
import goojeans.harulog.user.domain.entity.Follow;
import goojeans.harulog.user.domain.entity.UserGoal;
import goojeans.harulog.user.domain.entity.Users;
import goojeans.harulog.user.repository.UserGoalRepository;
import goojeans.harulog.user.repository.UserRepository;
import goojeans.harulog.user.util.JwtTokenProvider;
import goojeans.harulog.user.util.SecurityUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final CategoryRepository categoryRepository;
    private final UserGoalRepository userGoalRepository;

    @Override
    public Response<Void> signUp(SignUpRequest request) {

        String imageDefault = "image/profile/default.png";

        boolean nicknameDuplication = checkNicknameDuplication(request.getNickname());
        boolean emailDuplication = checkEmailDuplication(request.getEmail());

        if (nicknameDuplication && emailDuplication) {
            throw new BusinessException(ResponseCode.USER_NICKNAME_EMAIL_DUPLICATION);
        } else if (nicknameDuplication) {
            throw new BusinessException(ResponseCode.USER_NICKNAME_DUPLICATION);
        } else if (emailDuplication) {
            throw new BusinessException(ResponseCode.USER_EMAIL_DUPLICATION);
        }

        Users entity = request.toEntity();

        entity.updateImageUrl(imageDefault);
        entity.updatePassword(passwordEncoder.encode(entity.getPassword()));

        userRepository.save(entity);

        List<Category> allCategory = categoryRepository.findAll();
        allCategory.forEach(category ->
                        userGoalRepository.save(UserGoal.of(entity, category, 0)));

        return Response.ok();
    }

    @Override
    public Response<UserInfoEditResponse> getUserInfoForEdit() {
        JwtUserDetail currentUserInfo = securityUtils.getCurrentUserInfo();

        LocalDateTime createdAtLocalTime = currentUserInfo.getCreatedAt();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String createdAtFormat = createdAtLocalTime.format(formatter);

        UserInfoEditResponse response = UserInfoEditResponse.builder()
                .email(currentUserInfo.getEmail())
                .userName(currentUserInfo.getUsername())
                .createdAt(createdAtFormat)
                .introduction(currentUserInfo.getIntroduction())
                .nickname(currentUserInfo.getNickname())
                .contactNumber(currentUserInfo.getContactNumber())
                .imageUrl(currentUserInfo.getImageUrl())
                .socialType(currentUserInfo.getSocialType())
                .build();

        return Response.ok(response);
    }

    @Override
    public String updateUserInfo(UpdateUserInfoRequest request) {
        JwtUserDetail currentUserInfo = securityUtils.getCurrentUserInfo();

        Users users = userRepository.findById(currentUserInfo.getId()).stream()
                .findAny()
                .orElseThrow(() -> new BusinessException(ResponseCode.USER_NOT_FOUND));

        users.updateNickname(request.getNickname());
        users.updateIntroduction(request.getIntroduction());
        users.updateContactNumber(request.getContactNumber());

        Authentication auth = jwtTokenProvider.createAuthentication(users);

        return jwtTokenProvider.generateAccessToken(auth);
    }

    @Override
    public Response<Void> updatePassword(UpdatePasswordRequest request) {
        JwtUserDetail currentUserInfo = securityUtils.getCurrentUserInfo();

        if (!passwordEncoder.matches(request.getBeforePassword(), currentUserInfo.getPassword())) {
            throw new BusinessException(ResponseCode.USER_PASSWORD_NOT_MATCH);
        }

        Users user = userRepository.findById(currentUserInfo.getId()).stream()
                .findAny()
                .orElseThrow(() -> new BusinessException(ResponseCode.USER_NOT_FOUND));

        user.updatePassword(passwordEncoder.encode(request.getAfterPassword()));

        return Response.ok();
    }

    // confirm string is email
    @Override
    public Response<Void> delete(DeleteUserRequest request) {
        JwtUserDetail currentUserInfo = securityUtils.getCurrentUserInfo();

        if (!request.getConfirmString().equals(currentUserInfo.getEmail())) {
            throw new BusinessException(ResponseCode.USER_DELETE_STRING_NOT_MATCH);
        }

        Users user = userRepository.findById(currentUserInfo.getId()).stream()
                .findAny()
                .orElseThrow(() -> new BusinessException(ResponseCode.USER_NOT_FOUND));

        userRepository.delete(user);

        return Response.ok();
    }

    @Override
    public Response<MyPageInfoResponse> getMyPageUserInfo(String nickname) {

        JwtUserDetail currentUserInfo = securityUtils.getCurrentUserInfo();

        Users findUser = userRepository.findByNickname(nickname).stream()
                .findAny()
                .orElseThrow(() -> new BusinessException(ResponseCode.USER_NOT_FOUND));

        Set<Follow> followers = findUser.getFollowers();
        boolean present = followers != null && followers.stream()
                .anyMatch(follow ->
                        follow.getFollower() != null &&
                                follow.getFollower().getId().equals(currentUserInfo.getId()));

        MyPageInfoResponse response = MyPageInfoResponse.entityToResponse(findUser);
        if (present){
            response.setFollowing(true);
        }

        return Response.ok(response);
    }

    @Override
    public Response<Void> logout() {

        String logoutToken = "logout";

        JwtUserDetail currentUserInfo = securityUtils.getCurrentUserInfo();

        Users user = userRepository.findById(currentUserInfo.getId()).stream()
                .findAny()
                .orElseThrow(() -> new BusinessException(ResponseCode.USER_NOT_FOUND));

        user.updateRefreshToken(logoutToken);

        return Response.ok();

    }

    private boolean checkNicknameDuplication(String nickname) {
        return userRepository.findUsersByNickname(nickname).isPresent();
    }

    private boolean checkEmailDuplication(String email) {
        return userRepository.findUsersByEmail(email).isPresent();
    }
}

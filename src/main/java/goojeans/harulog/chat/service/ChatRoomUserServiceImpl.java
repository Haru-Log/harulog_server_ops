package goojeans.harulog.chat.service;

import goojeans.harulog.chat.domain.dto.ChatRoomDTO;
import goojeans.harulog.chat.domain.dto.ChatUserDTO;
import goojeans.harulog.chat.domain.dto.MessageDTO;
import goojeans.harulog.chat.domain.entity.ChatRoom;
import goojeans.harulog.chat.domain.entity.ChatRoomUser;
import goojeans.harulog.chat.domain.entity.Message;
import goojeans.harulog.chat.repository.ChatRoomRepository;
import goojeans.harulog.chat.repository.ChatRoomUserRepository;
import goojeans.harulog.chat.repository.MessageRepository;
import goojeans.harulog.config.RabbitMQConfig;
import goojeans.harulog.domain.BusinessException;
import goojeans.harulog.domain.ResponseCode;
import goojeans.harulog.domain.dto.Response;
import goojeans.harulog.user.domain.entity.Users;
import goojeans.harulog.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

import static goojeans.harulog.chat.util.ChatRoomType.DM;
import static goojeans.harulog.chat.util.ChatRoomType.GROUP;
import static goojeans.harulog.chat.util.MessageType.ENTER;
import static goojeans.harulog.chat.util.MessageType.EXIT;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomUserServiceImpl implements ChatRoomUserService {

    private final ChatRoomUserRepository chatRoomUserRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    // 입장, 퇴장 메세지 저장
    private final MessageRepository messageRepository;

    // 입장, 퇴장 메세지 전송
    private final RabbitTemplate rabbitTemplate;

    // 채팅방 - 유저 binding, unbinding
    private final RabbitMQConfig rabbitMQConfig;

    /**
     * 채팅방에 유저 1명 추가
     * + DM방 일때 유저가 1명 추가되어, 2명 초과가 되면 Group 채팅방으로 변경
     */
    @Override
    public Response<Void> addUser(String roomId, String userNickname) {

        ChatRoom chatRoom = findChatRoom(roomId);
        Users user = findUser(userNickname);

        chatRoomUserRepository.save(ChatRoomUser.create(chatRoom, user));

        // DM방 일때 유저가 1명 추가되어, 2명 초과가 되면 Group 채팅방으로 변경
        if(chatRoom.getType() == DM && chatRoom.getUsers().size()+1 > 2){
            chatRoom.setType(GROUP);
            chatRoomRepository.save(chatRoom);
        }

        // 채팅방 Exchange에 유저 Queue BINDING
        rabbitMQConfig.binding(roomId, userNickname);

        // 입장 메세지 전송
        sendEnterMessage(chatRoom, user);

        return Response.ok();
    }

    /**
     * 채팅방에 유저 여러명 추가
     * + DM방 일때 유저가 추가되어 2명 초과이면 Group 채팅방으로 변경
     */
    @Override
    public Response<Void> addUser(String roomId, List<String> usersNickname) {

        ChatRoom chatRoom = findChatRoom(roomId);
        List<Users> users = usersNickname.stream()
                .map(this::findUser)
                .toList();

        users.forEach(user -> chatRoomUserRepository.save(ChatRoomUser.create(chatRoom, user)));

        // 채팅방 Exchange에 유저 Queue BINDING
        users.forEach(user -> rabbitMQConfig.binding(roomId, user.getNickname()));

        // 입장 메세지 전송
        users.forEach(user -> sendEnterMessage(chatRoom, user));

        // DM방 일때 유저가 추가되어 2명 초과이면 Group 채팅방으로 변경
        if (chatRoom.getType() == DM && chatRoom.getUsers().size() + users.size() > 2) {
            chatRoom.setType(GROUP);
            chatRoomRepository.save(chatRoom);
        }

        return Response.ok();
    }

    /**
     * 채팅방에 유저 삭제
     * + 그룹 채팅방이면서 유저가 1명 빠져서, 2명 이하로 되면 DM 채팅방으로 변경
     */
    @Override
    public Response<Void> deleteUser(String roomId, String userNickname) {

        ChatRoomUser chatRoomUser = findChatRoomUser(roomId, userNickname);
        chatRoomUserRepository.delete(chatRoomUser);

        // 그룹 채팅방이면서 유저가 1명 빠져서, 2명 이하로 되면 DM 채팅방으로 변경
        ChatRoom chatRoom = chatRoomUser.getChatRoom();
        if(chatRoom.getType() == GROUP && chatRoom.getUsers().size()-1 <= 2){
            chatRoom.setType(DM);
            chatRoomRepository.save(chatRoom);
        }

        // 퇴장 메세지 전송
        sendExitMessage(chatRoom, chatRoomUser.getUser());

        // 채팅방 Exchange에 유저 Queue UNBINDING
        rabbitMQConfig.unBinding(roomId, userNickname);

        return Response.ok();
    }

    /**
     * 채팅방에 참여하고 있는 유저 조회
     * @return [유저 닉네임, 이미지url] 리스트
     */
    @Override
    public Response<List<ChatUserDTO>> getUsers(String roomId) {
        List<Users> userList = chatRoomUserRepository.findUserByChatroomId(roomId);

        return Response.ok(userList.stream()
                .map(ChatUserDTO::of)
                .toList());
    }

    /**
     * 유저가 참여하고 있는 채팅방 조회
     */
    @Override
    public Response<List<ChatRoomDTO>> getChatRooms(String userNickname) {

        List<ChatRoom> chatRoomList = chatRoomUserRepository.findChatRoomsByUserNickName(userNickname);
        List<ChatRoomDTO> chatRoomDTOList = chatRoomList.stream()
                .sorted(Comparator.naturalOrder()) // Comparable에 의한 정렬
                .map(ChatRoomDTO::of)
                .toList();

        return Response.ok(chatRoomDTOList);
    }

    /**
     * 입장 메세지 생성, 저장 및 전송
     */
    @Override
    public void sendEnterMessage(ChatRoom room, Users user) {

        // 입장 메세지 생성 및 저장
        String content = user.getNickname() + "님이 들어오셨습니다.";
        Message message = Message.create(room, user, ENTER, content);
        messageRepository.save(message);

        // 채팅방 Exchange에 입장 메세지 전송
        rabbitTemplate.convertAndSend("chatroom."+room.getId(),"", MessageDTO.of(message));
    }

    /**
     * 퇴장 메세지 생성, 저장 및 전송
     */
    @Override
    public void sendExitMessage(ChatRoom room, Users user) {

            // 퇴장 메세지 생성 및 저장
            String content = user.getNickname() + "님이 나가셨습니다.";
            Message message = Message.create(room, user, EXIT, content);
            messageRepository.save(message);

            // 채팅방 Exchange에 퇴장 메세지 전송
            rabbitTemplate.convertAndSend("chatroom."+room.getId(),"", MessageDTO.of(message));
    }

    /**
     * 유저, 채팅방, 채팅방-유저 권한 조회
     */
    private Users findUser(String userNickname) {
        return userRepository.findUsersByNickname(userNickname)
                .orElseThrow(() -> new BusinessException(ResponseCode.USER_NOT_FOUND));
    }

    private ChatRoom findChatRoom(String roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ResponseCode.CHATROOM_NOT_FOUND));
    }

    private ChatRoomUser findChatRoomUser(String roomId, String userNickname) {
        Users user = findUser(userNickname);
        findChatRoom(roomId);
        return chatRoomUserRepository.findByChatRoomIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new BusinessException(ResponseCode.CHAT_NO_PERMISSION));
    }
}
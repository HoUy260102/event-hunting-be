package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.InteractionType;
import com.example.event.entity.Event;
import com.example.event.entity.Favorite;
import com.example.event.entity.User;
import com.example.event.exception.AppException;
import com.example.event.repository.EventRepository;
import com.example.event.repository.FavoriteRepository;
import com.example.event.repository.UserRepository;
import com.example.event.service.EventInteractionService;
import com.example.event.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FavoriteServiceImpl implements FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final EventInteractionService eventInteractionService;

    @Override
    @Transactional
    public void addFavorite(String eventId) {
        String userId = securityUtils.getCurrentUserId();
        if (favoriteRepository.existsByUser_IdAndEvent_Id(userId, eventId)) {
            throw new AppException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));

        // 2. Lưu bản ghi mới
        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setEvent(event);
        favoriteRepository.save(favorite);

        eventInteractionService.addInteraction(eventId, userId, InteractionType.FAVORITE);
    }

    @Override
    @Transactional
    public void removeFavorite(String eventId) {
        String userId = securityUtils.getCurrentUserId();
        if (!favoriteRepository.existsByUser_IdAndEvent_Id(userId, eventId)) {
            throw new AppException(ErrorCode.FAVORITE_NOT_FOUND);
        }
        Favorite favorite = favoriteRepository.findFavoriteByUser_IdAndEvent_Id(userId, eventId);
        favoriteRepository.delete(favorite);

        eventInteractionService.addInteraction(eventId, userId, InteractionType.FAVORITE);
    }
}

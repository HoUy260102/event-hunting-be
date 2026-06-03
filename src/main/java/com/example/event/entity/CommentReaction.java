package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import com.example.event.constant.ReactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
    name = "comment_reaction",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"comment_id", "user_id"})
    }
)
public class CommentReaction {
    @Id
    @UlidID
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 20)
    private ReactionType reactionType;

    private LocalDateTime createdAt = LocalDateTime.now();
}

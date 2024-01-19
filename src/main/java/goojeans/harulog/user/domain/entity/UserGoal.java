package goojeans.harulog.user.domain.entity;

import goojeans.harulog.domain.entity.BaseEntity;
import goojeans.harulog.category.domain.entity.Category;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class UserGoal extends BaseEntity {

    @EmbeddedId
    private UserGoalId userGoalId;

    @MapsId("userId")
    @ManyToOne
    @JoinColumn(name = "user_id")
    private Users user;

    @MapsId("categoryId")
    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    private String goal;

}

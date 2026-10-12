package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog_comments")
public class BlogComments implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * User ID
     */
    private Long userId;

    /**
     * Blog ID
     */
    private Long blogId;

    /**
     * Parent top-level comment ID (0 for top-level comments)
     */
    private Long parentId;

    /**
     * Replied-to comment ID
     */
    private Long answerId;

    /**
     * Reply content
     */
    private String content;

    /**
     * Like count
     */
    private Integer liked;

    /**
     * Status, 0: normal, 1: reported, 2: hidden
     */
    private Boolean status;

    /**
     * Created at
     */
    private LocalDateTime createTime;

    /**
     * Updated at
     */
    private LocalDateTime updateTime;


}

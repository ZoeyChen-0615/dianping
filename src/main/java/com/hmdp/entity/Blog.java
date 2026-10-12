package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("tb_blog")
public class Blog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /**
     * Shop ID
     */
    private Long shopId;
    /**
     * User ID
     */
    private Long userId;
    /**
     * User avatar
     */
    @TableField(exist = false)
    private String icon;
    /**
     * Username
     */
    @TableField(exist = false)
    private String name;
    /**
     * Whether the current user has liked it
     */
    @TableField(exist = false)
    private Boolean isLike;

    /**
     * Title
     */
    private String title;

    /**
     * Visit photos, up to 9, comma-separated
     */
    private String images;

    /**
     * Text description of the visit
     */
    private String content;

    /**
     * Like count
     */
    private Integer liked;

    /**
     * Comment count
     */
    private Integer comments;

    /**
     * Created at
     */
    private LocalDateTime createTime;

    /**
     * Updated at
     */
    private LocalDateTime updateTime;


}

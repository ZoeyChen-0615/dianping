package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hmdp.dto.UserDTO;
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
@TableName("tb_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Phone number or email used for login
     */
    private String phone;

    /**
     * Password, stored encrypted
     */
    private String password;

    /**
     * Nickname, random by default
     */
    private String nickName;

    /**
     * User avatar
     */
    private String icon = "";

    /**
     * Created at
     */
    private LocalDateTime createTime;

    /**
     * Updated at
     */
    private LocalDateTime updateTime;


}

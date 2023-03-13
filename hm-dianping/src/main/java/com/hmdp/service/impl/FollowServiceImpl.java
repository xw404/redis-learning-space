package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    //业务层实现关注的方法
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //1 判断到底是关注还是取关
        if (isFollow) {
            //2，关注。新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess){
                //如果成功，把关注用户的id放入Redis集合中 sadd userID followUserId
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else {
            //3，取关。删除数据  delete from tb_follow where user_id = ? follow_user_id=?
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (isSuccess) {
                //把关注的用户Id从redis集合中移出
                stringRedisTemplate.opsForSet().remove(key, followUserId);
            }
        }
        return Result.ok();
    }
    /**
     * 共同关注
     * 参数为目标用户的id,查的是目标用户与当前用户的共同关注
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //求交集
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect==null||intersect.isEmpty()){
            //无交集
            return Result.ok(Collections.emptyList());
        }
        //解析出Id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    //判断有没有关注的方法
    @Override
    public Result isFollow(Long followUserId) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //1 查询是否关注select count  from tb_follow where user_id = ? follow_user_id=?
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        //2 如果Count>0，说明有关注
        return Result.ok(count>0);
    }

}

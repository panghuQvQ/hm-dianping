package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * @author admin
 * @version 1.0.0
 * @ClassName ShopControllerTest.java
 * @Description TODO 项目运行前需做：把商铺根据类型分组，并将其经纬度存入 Redis 中
 * @createTime 2023年03月07日 15:02:00
 */
@SpringBootTest
public class ShopControllerTest {

    @Autowired
    private IShopService shopService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void importShopToRedis() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照 typeId分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写入 Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1 获取类型ID
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2 获取同类型的店铺集合
            List<Shop> shopList = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            // 3.1 写入 redis GEOADD key 经度 纬度 member
            for (Shop shop : shopList) {
//                stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<String>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())));
            }

            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
}

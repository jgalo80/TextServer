package net.jgn.cliptext.server.repo;

import net.jgn.cliptext.server.user.User;
import org.apache.ibatis.annotations.*;

/**
 * @author jose
 */
public interface UserRepository {

    String CREATE_TABLE =
            "CREATE TABLE APP_USER (" +
            "   ID INTEGER NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)," +
            "   USER_NAME VARCHAR(30), " +
            "   HASH_PASSWORD VARCHAR(64)" +
            ")";


    @Select("select ID, USER_NAME, HASH_PASSWORD from APP_USER where id = #{id}")
    User selectById(Integer id);

    @Select("select ID, USER_NAME, HASH_PASSWORD from APP_USER where user_name = #{userName}")
    User selectByUserName(String userName);

    @Update(CREATE_TABLE)
    void createTable();

    @Update("CREATE UNIQUE INDEX UI_APP_USER_USERNAME on APP_USER (USER_NAME)")
    void createUserNameIndex();

    @Insert("INSERT INTO APP_USER (USER_NAME, HASH_PASSWORD) VALUES (#{userName}, #{hashPassword})")
    @SelectKey(statement = "values IDENTITY_VAL_LOCAL()", keyProperty = "id", resultType=Integer.class, before = false)
    void insertUser(User user);
}

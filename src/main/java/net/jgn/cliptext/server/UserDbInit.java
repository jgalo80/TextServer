package net.jgn.cliptext.server;

import net.jgn.cliptext.server.repo.UserRepository;
import net.jgn.cliptext.server.user.User;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLSyntaxErrorException;

/**
 * @author jose
 */
@Component
public class UserDbInit {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    public void createDbIfNotExists() {
        SqlSession session = sqlSessionFactory.openSession(true);
        UserRepository userRepository = null;
        try {
            userRepository = session.getMapper(UserRepository.class);
            User user = userRepository.selectById(1);
        } catch (PersistenceException ex) {
            if (userRepository != null && ex.getCause() != null) {
                if (ex.getCause() instanceof SQLSyntaxErrorException) {
                    SQLSyntaxErrorException sqlSyntaxErrorException = (SQLSyntaxErrorException) ex.getCause();
                    if ("42Y07".equals(sqlSyntaxErrorException.getSQLState())) {
                        logger.warn("Table APP_USER doesn't exist. Creating table...");
                        userRepository.createTable();
                        userRepository.createUserNameIndex();
                    } else {
                        logger.error("Persistence error (SQLSyntaxErrorException).", ex);
                    }
                } else {
                    logger.error("Persistence error.", ex);
                }
            } else {
                logger.error("Persistence error (unknown cause).", ex);
            }
        } finally {
            session.close();
        }
    }
}

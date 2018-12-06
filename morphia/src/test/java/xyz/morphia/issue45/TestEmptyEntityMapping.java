package xyz.morphia.issue45;


import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Embedded;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Transient;
import xyz.morphia.testutil.TestEntity;

import java.util.HashSet;
import java.util.Set;


public class TestEmptyEntityMapping extends TestBase {

    @Test
    public void testSizeOnEmptyElements() {
        User u = new User();
        u.setFullName("User Name");
        u.setUserId("USERID");
        getDatastore().save(u);

        Assert.assertNull("Should not find the user.", getDatastore().find(User.class).filter("rights size", 0).first());
        Assert.assertNull("Should not find the user.", getDatastore().find(User.class).field("rights").sizeEq(0).first());
        Assert.assertNotNull("Should find the user.", getDatastore().find(User.class).field("rights").doesNotExist().first());
        getDatastore().deleteMany(getDatastore().find(User.class));

        u = new User();
        u.setFullName("User Name");
        u.setUserId("USERID");
        u.getRights().add(Rights.ADMIN);
        getDatastore().save(u);

        Assert.assertNotNull("Should find the user.", getDatastore().find(User.class).filter("rights size", 1).first());
        Assert.assertNotNull("Should find the user.", getDatastore().find(User.class).field("rights").sizeEq(1).first());
        Assert.assertNotNull("Should find the user.", getDatastore().find(User.class).field("rights").exists().first());
    }


    @Entity
    public enum Rights {
        ADMIN
    }

    @Entity
    static class A extends TestEntity {
        private B b;
    }

    @Embedded
    static class B {
        @Transient
        private String foo;
    }

    @Entity
    public static class UserType extends TestEntity {
    }

    @Entity
    public static class NotificationAddress extends TestEntity {
    }

    @Entity
    public static class User extends TestEntity {

        private String userId = null;
        private String fullName = null;
        private UserType userType = null;
        private Set<Rights> rights = new HashSet<>();
        private Set<NotificationAddress> notificationAddresses = new HashSet<>();

        public String getFullName() {
            return fullName;
        }

        public void setFullName(final String fullName) {
            this.fullName = fullName;
        }

        public Set<NotificationAddress> getNotificationAddresses() {
            return notificationAddresses;
        }

        public void setNotificationAddresses(final Set<NotificationAddress> notificationAddresses) {
            this.notificationAddresses = notificationAddresses;
        }

        public Set<Rights> getRights() {
            return rights;
        }

        public void setRights(final Set<Rights> rights) {
            this.rights = rights;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(final String userId) {
            this.userId = userId;
        }

        public UserType getUserType() {
            return userType;
        }

        public void setUserType(final UserType userType) {
            this.userType = userType;
        }
    }
}

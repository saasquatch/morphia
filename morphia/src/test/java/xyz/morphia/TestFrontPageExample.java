/*
  Copyright (C) 2010 Olafur Gauti Gudmundsson
  <p/>
  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
  obtain a copy of the License at
  <p/>
  http://www.apache.org/licenses/LICENSE-2.0
  <p/>
  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
  and limitations under the License.
 */


package xyz.morphia;


import com.mongodb.client.result.UpdateResult;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.annotations.Entity;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.Indexed;
import xyz.morphia.annotations.NotSaved;
import xyz.morphia.annotations.Property;
import xyz.morphia.annotations.Reference;
import xyz.morphia.annotations.Transient;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TestFrontPageExample extends TestBase {

    @Test
    public void testIt() {
        getMapper().map(Employee.class);

        getDatastore().save(new Employee("Mister", "GOD", null, 0));

        final Employee boss = getDatastore().find(Employee.class).field("manager").equal(null).first(); // get an employee without a manager
        Assert.assertNotNull(boss);
        final Key<Employee> key = getDatastore().save(new Employee("Scott", "Hernandez", getMapper().getKey(boss), 150 * 1000));
        Assert.assertNotNull(key);

        final UpdateResult res = getDatastore().update(boss, getDatastore().createUpdateOperations(Employee.class)
                                                                           .addToSet("underlings", key));
        Assert.assertNotNull(res);
        Assert.assertTrue("Should update existing document", res.getModifiedCount() > 0);
        Assert.assertEquals("Should update one document", 1, res.getModifiedCount());

        final Employee scottsBoss = getDatastore().find(Employee.class).filter("underlings", key).first();
        Assert.assertNotNull(scottsBoss);
        Assert.assertEquals(boss.id, scottsBoss.id);
    }

    @Entity("employees")
    private static class Employee {

        @Reference
        private final List<Employee> underlings = new ArrayList<>(); // refs are stored*, and loaded automatically
        private final transient boolean stored = true; // not @Transient, will be ignored by Serialization/GWT for example.
        @Id
        private ObjectId id; // auto-generated, if not set (see ObjectId)
        private String firstName;
        // Address address; // by default fields are @Embedded
        private String lastName; // value types are automatically persisted
        private Long salary; // only non-null values are stored
        private Key<Employee> manager; // references can be saved without automatic
        @Property("started")
        private Date startDate; // fields can be renamed
        @Property("left")
        private Date endDate;
        @Indexed
        private boolean active; // fields can be indexed for better performance
        @NotSaved
        private String readButNotStored; // fields can loaded, but not saved
        @Transient
        private int notStored; // fields can be ignored (no load/save)

        Employee() {
        }

        Employee(final String f, final String l, final Key<Employee> boss, final long sal) {
            firstName = f;
            lastName = l;
            manager = boss;
            salary = sal;
        }
    }
}

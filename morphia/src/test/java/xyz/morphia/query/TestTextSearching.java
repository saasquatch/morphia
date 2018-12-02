package xyz.morphia.query;

import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import xyz.morphia.TestBase;
import xyz.morphia.annotations.Field;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.Index;
import xyz.morphia.annotations.Indexes;
import xyz.morphia.utils.IndexType;

import java.util.List;

import static java.util.Arrays.asList;
import static xyz.morphia.query.Sort.ascending;

public class TestTextSearching extends TestBase {
    @Test
    public void testTextSearch() {
        getMapper().map(Greeting.class);
        getDatastore().ensureIndexes();

        getDatastore().save(new Greeting("good morning", "english"));
        getDatastore().save(new Greeting("good afternoon", "english"));
        getDatastore().save(new Greeting("good night", "english"));
        getDatastore().save(new Greeting("good riddance", "english"));

        getDatastore().save(new Greeting("guten Morgen", "german"));
        getDatastore().save(new Greeting("guten Tag", "german"));
        getDatastore().save(new Greeting("gute Nacht", "german"));

        getDatastore().save(new Greeting("buenos días", "spanish"));
        getDatastore().save(new Greeting("buenas tardes", "spanish"));
        getDatastore().save(new Greeting("buenos noches", "spanish"));

        List<Greeting> good = getDatastore().find(Greeting.class)
                                            .search("good")
                                            .order(ascending("_id"))
                                            .asList();
        Assert.assertEquals(4, good.size());
        Assert.assertEquals("good morning", good.get(0).value);
        Assert.assertEquals("good afternoon", good.get(1).value);
        Assert.assertEquals("good night", good.get(2).value);
        Assert.assertEquals("good riddance", good.get(3).value);

        good = getDatastore().find(Greeting.class)
                             .search("good", "english")
                             .order(ascending("_id"))
                             .asList();
        Assert.assertEquals(4, good.size());
        Assert.assertEquals("good morning", good.get(0).value);
        Assert.assertEquals("good afternoon", good.get(1).value);
        Assert.assertEquals("good night", good.get(2).value);
        Assert.assertEquals("good riddance", good.get(3).value);

        Assert.assertEquals(1, getDatastore().find(Greeting.class)
                                             .search("riddance")
                                             .asList().size());
        Assert.assertEquals(1, getDatastore().find(Greeting.class)
                                             .search("noches", "spanish")
                                             .asList().size());
        Assert.assertEquals(1, getDatastore().find(Greeting.class)
                                             .search("Tag")
                                             .asList().size());
    }

    @Test
    public void testTextSearchSorting() {
        getMapper().map(Book.class);
        getDatastore().ensureIndexes();

        getDatastore().saveMany(asList(new Book("The Banquet", "Dante"),
                            new Book("Divine Comedy", "Dante"),
                            new Book("Eclogues", "Dante"),
                            new Book("The Odyssey", "Homer"),
                            new Book("Iliad", "Homer")));

        List<Book> books = getDatastore().find(Book.class)
                                         .search("Dante Comedy").project(Meta.textScore("score"))
                                         .order(Meta.textScore("score"))
                                         .asList();
        Assert.assertEquals(3, books.size());
        Assert.assertEquals("Divine Comedy", books.get(0).title);
    }

    @Test
    public void testTextSearchValidationFailed() {
        getMapper().map(Book.class);
        getDatastore().ensureIndexes();

        getDatastore().saveMany(asList(new Book("The Banquet", "Dante"),
                            new Book("Divine Comedy", "Dante"),
                            new Book("Eclogues", "Dante"),
                            new Book("The Odyssey", "Homer"),
                            new Book("Iliad", "Homer")));

        List<Book> books = getDatastore().find(Book.class)
                                         .search("Dante").project(Meta.textScore())
                                         .order(Meta.textScore())
                                         .asList();
        Assert.assertEquals(3, books.size());
        Assert.assertEquals("Dante", books.get(0).author);
    }

    @Test
    public void testTextSearchWithMeta() {
        getMapper().map(Book.class);
        getDatastore().ensureIndexes();

        getDatastore().saveMany(asList(new Book("The Banquet", "Dante"),
                            new Book("Divine Comedy", "Dante"),
                            new Book("Eclogues", "Dante"),
                            new Book("The Odyssey", "Homer"),
                            new Book("Iliad", "Homer")));

        List<Book> books = getDatastore().find(Book.class)
                                         .search("Dante").project(Meta.textScore("score"))
                                         .order(Meta.textScore("score"))
                                         .asList();
        Assert.assertEquals(3, books.size());
        for (Book book : books) {
            Assert.assertEquals("Dante", book.author);
        }
    }

    @Indexes(@Index(fields = @Field(value = "$**", type = IndexType.TEXT)))
    private static class Greeting {
        @Id
        private ObjectId id;
        private String value;
        private String language;

        Greeting() {
        }

        private Greeting(final String value, final String language) {
            this.language = language;
            this.value = value;
        }
    }

    @Indexes(@Index(fields = @Field(value = "$**", type = IndexType.TEXT)))
    private static class Book {
        @Id
        private ObjectId id;
        private String title;
        private String author;

        Book() {
        }

        private Book(final String title, final String author) {
            this.author = author;
            this.title = title;
        }
    }
}

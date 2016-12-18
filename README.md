SqliteMagic
=======

Simple yet powerful SQLite database layer for Android that makes database handling feel like magic.

#### Overview:
* Simple, intuitive & **typesafe API**
* Minimal [setup](https://github.com/SiimKinks/sqlitemagic/wiki/Setup) needed
* Built in [RxJava support](https://github.com/SiimKinks/sqlitemagic/wiki/RxJava-Support) with reactive stream semantics on queries and operations
* Built in [AutoValue](https://github.com/SiimKinks/sqlitemagic/wiki/Immutable-Objects) immutable objects support
* Full support for [complex columns](https://github.com/SiimKinks/sqlitemagic/wiki/User-Defined-Objects-as-Columns)
* Support for [SQLite views](https://github.com/SiimKinks/sqlitemagic/wiki/Views)
* Persist any third party object with fully customizable [object transformers](https://github.com/SiimKinks/sqlitemagic/wiki/Object-Transformers)
* Support for [migrations](https://github.com/SiimKinks/sqlitemagic/wiki/Migrations)
* **No reflection**
* Compile time annotation processing
* Probably the fastest library for Android SQLite database operations *(without memory caching)*

Getting Started
-----------------

#### Install IntelliJ Plugin:

The Intellij plugin can be installed from Android Studio by navigating
Android Studio -> Preferences -> Plugins -> Browse repositories -> Search for SqliteMagic

#### Add SqliteMagic to Project:

```groovy
buildscript {
  repositories {
    jcenter()
  }
  dependencies {
    classpath 'com.android.tools.build:gradle:<latest version>'
    classpath 'com.siimkinks.sqlitemagic:sqlitemagic-plugin:0.11.0'
  }
}

apply plugin: 'com.android.application'
apply plugin: 'com.siimkinks.sqlitemagic'
```

#### Initialize Library:

```java
SqliteMagic.init(applicationContext);
```
**Note**: any place with a reference to Application context is ok to use for initialization, but it must happen before a database is accessed. During initialization default db connection is opened, db schema is created and migration scripts are executed - no other hidden runtime performance costs.
 

#### [Define Database](https://github.com/SiimKinks/sqlitemagic/wiki/Defining-Database-Schema):

_Note that there is no need to extend or implement any base classes or interfaces_

<table style="width:100%; border-collapse: collapse;" >
  <tr>
    <th>POJO</th>
    <th>AutoValue</th>
  </tr>
  <tr style="background: none">
    <td style="padding:0; margin:0; border:none; width:50%;">
      <pre lang="java"><code class="language-java">@Table(persistAll = true)
public class Author {

  @Id(autoIncrement = false)
  long id;
  
  String firstName;
  
  String lastName;
  
  ...
}

@Table(persistAll = true)
public class Book {

  @Id(autoIncrement = false)
  long id();
  
  String title;
  
  Author author;
  
  ...
}


      </code></pre>
    </td>
    <td style="padding:0; margin:0; border:none; width:50%;">
      <pre lang="java"><code class="language-java">@Table(persistAll = true)
@AutoValue
public abstract class Author {

  @Id(autoIncrement = false)
  public abstract long id();
  
  public abstract String firstName();
  
  public abstract String lastName();
  
  ...
}

@Table(persistAll = true)
@AutoValue
public abstract class Book {

  @Id(autoIncrement = false)
  public abstract long id();
  
  public abstract String title();
  
  public abstract Author author();
  
  ...
}
      </code></pre>
    </td>
  </tr>
</table>

**Database operation builder methods are "automagically" [generated](https://github.com/SiimKinks/sqlitemagic/wiki/Database-Operations) during compile time on objects with `@Table` annotation using bytecode manipulation and AST transformations. These methods may seem like "magic", but actually they are only glue methods that call corresponding table generated class methods. This way one can still see human readable code during debugging - just press "step into" when magic method is encountered.**
 
#### [Do Operations With Objects](https://github.com/SiimKinks/sqlitemagic/wiki/Database-Operations):

<table style="width:100%; border-collapse: collapse;" >
  <tr>
    <th>Synchronous</th>
    <th>RxJava</th>
  </tr>
  <tr style="background: none">
    <td style="padding:0; margin:0; border:none; width:50%;">
      <pre lang="java"><code class="language-java">Author author = new Author(73, "Foo", "Bar");
Book book = new Book(77, "Bar", author);

// insert -- NOTE: author object also gets
// inserted and the whole operation
// is wrapped in transaction
long id = book
    .insert()
    .execute();

// update
boolean success = author
    .update()
    .execute();

// update or insert
id = author
    .persist()
    .execute();
    
// update or insert but ignore null values
id = author
    .persist()
    .ignoreNullValues()
    .execute();
    
// delete
int nrOfDeletedRows = author
    .delete()
    .execute();
    
// Bulk operations are also supported
success = Author
    .persist(someAuthors)
    .ignoreNullValues()
    .execute();

</code></pre>
    </td>
    <td style="padding:0; margin:0; border:none; width:50%;">
      <pre lang="java"><code class="language-java">Author author = new Author(73, "Foo", "Bar");
Book book = new Book(77, "Bar", author);

// insert -- NOTE: author object also gets
// inserted and the whole operation is
// wrapped in transaction when result
// object gets subscribed
Single&lt;Long&gt; insert = book
    .insert()
    .observe();

// update
Completable update = author
    .update()
    .observe();

// update or insert
Single&lt;Long&gt; persist = author
    .persist()
    .observe();
    
// update or insert but ignore null values
persist = author
    .persist()
    .ignoreNullValues()
    .observe();
    
// delete
Single&lt;Integer&gt; delete = author
    .delete()
    .observe();
    
// Bulk operations are also supported
Completable bulkPersist = Author
    .persist(someAuthors)
    .ignoreNullValues()
    .observe();</code></pre>
    </td>
  </tr>
</table>

(All database operations trigger [RxJava notifications](https://github.com/SiimKinks/sqlitemagic/wiki/RxJava-Support) on active queries that listen to table that is being modified)

#### Use Typesafe Operation Builders:

<table style="width:100%; border-collapse: collapse;" >
  <tr>
    <th>Synchronous</th>
    <th>RxJava</th>
  </tr>
  <tr style="background: none">
    <td style="padding:0; margin:0; border:none; width:50%;">
      <pre lang="java"><code class="language-java">import static com.siimkinks.sqlitemagic.BookTable.BOOK;
...

int nrOfUpdatedRows = Update
    .table(BOOK)
    .set(BOOK.TITLE, "Foo")
    .where(BOOK.ID.is(77L))
    .execute();

int nrOfDeletedRows = Delete
    .from(BOOK)
    .where(BOOK.ID.isNot(77L)
        .and(BOOK.TITLE.is("Foo")))
    .execute();
</code></pre>
    </td>
    <td style="padding:0; margin:0; border:none; width:50%;">
      <pre lang="java"><code class="language-java">import static com.siimkinks.sqlitemagic.BookTable.BOOK;
...

Single&lt;Integer&gt; update = Update
    .table(BOOK)
    .set(BOOK.TITLE, "Foo")
    .where(BOOK.ID.is(77L))
    .observe();

Single&lt;Integer&gt; delete = Delete
    .from(BOOK)
    .where(BOOK.ID.isNot(77L)
        .and(BOOK.TITLE.is("Foo")))
    .observe();</code></pre>
    </td>
  </tr>
</table>

#### [Query Data](https://github.com/SiimKinks/sqlitemagic/wiki/Querying-Objects):
SqliteMagic ships with its own [DSL](https://github.com/SiimKinks/sqlitemagic/wiki/SQL-Building) (or	Domain Specific Language) that emulates SQL in Java (inspired by [JOOQ](http://www.jooq.org/)).

<table style="width:100%; border-collapse: collapse;" >
  <tr>
    <th>Synchronous</th>
    <th>RxJava</th>
  </tr>
  <tr style="background: none">
    <td style="padding:0; margin:0; border:none; width:50%;">
      <pre lang="java"><code class="language-java">import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
...

List&lt;Author&gt; authors = Select
    .from(AUTHOR)
    .where(AUTHOR.FIRST_NAME.like("Foo%")
        .and(AUTHOR.LAST_NAME.isNot("Bar")))
    .orderBy(AUTHOR.LAST_NAME.desc())
    .limit(10)
    .execute();


</code></pre>
    </td>
    <td style="padding:0; margin:0; border:none; width:50%;">
      <pre lang="java"><code class="language-java">import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
...

// QueryObservable is an rx.Observable of Query
// which offers query-specific convenience
// operators.
QueryObservable&lt;List&lt;Author&gt;&gt; observable = Select
    .from(AUTHOR)
    .where(AUTHOR.FIRST_NAME.like("Foo%")
        .and(AUTHOR.LAST_NAME.isNot("Bar")))
    .orderBy(AUTHOR.LAST_NAME.desc())
    .limit(10)
    .observe();</code></pre>
    </td>
  </tr>
</table>

#### [Query Complex Data](https://github.com/SiimKinks/sqlitemagic/wiki/Automatic-SQL-Perfecting):

<table style="width:100%; border-collapse: collapse;" >
  <tr>
    <th>Synchronous</th>
    <th>RxJava</th>
  </tr>
  <tr style="background: none">
    <td style="padding:0; margin:0; border:none; width:50%;">
      <pre lang="java"><code class="language-java">import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
...

// the resulting Book objects also contain
// Author objects
List&lt;Book&gt; books = Select
    .from(BOOK)
    .where(BOOK.TITLE.is("Bar")
        .and(AUTHOR.is(someAuthorObject)))
    .orderBy(AUTHOR.LAST_NAME.asc())
    .limit(10)
    // this tells to query all complex data
    // which is queried in a single
    // SELECT statement
    .queryDeep()
    .execute();</code></pre>
    </td>
    <td style="padding:0; margin:0; border:none; width:50%;">
      <pre lang="java"><code class="language-java">import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
...

// the resulting Book objects also contain
// Author objects
QueryObservable&lt;List&lt;Book&gt;&gt; observable = Select
    .from(BOOK)
    .where(BOOK.TITLE.is("Bar")
        .and(AUTHOR.is(someAuthorObject)))
    .orderBy(AUTHOR.LAST_NAME.asc())
    .limit(10)
    // this tells to query all complex data
    // which is queried in a single
    // SELECT statement
    .queryDeep()
    .observe();</code></pre>
    </td>
  </tr>
</table>

There is so much more to querying data like [SQL functions](https://github.com/SiimKinks/sqlitemagic/wiki/Column-Expressions), [views](https://github.com/SiimKinks/sqlitemagic/wiki/Views), more [type safety](https://github.com/SiimKinks/sqlitemagic/wiki/Generated-Tables), [selecting columns](https://github.com/SiimKinks/sqlitemagic/wiki/Querying-Specific-Columns), querying only [the first result](https://github.com/SiimKinks/sqlitemagic/wiki/Take-First), [counting](https://github.com/SiimKinks/sqlitemagic/wiki/Count), RxJava [convenience operators](https://github.com/SiimKinks/sqlitemagic/wiki/RxJava-Support), etc.
**Take a deeper look at the [wiki](https://github.com/SiimKinks/sqlitemagic/wiki).**

Documentation
-----------------

- **[Wiki](https://github.com/SiimKinks/sqlitemagic/wiki)**
- [Javadoc](https://siimkinks.github.io/sqlitemagic/javadoc/)

Updates
------------

All updates can be found in the [CHANGELOG](CHANGELOG.md).

Bugs and Feedback
-----------------

**For bugs, questions and discussions please use the [Github Issues](https://github.com/SiimKinks/sqlitemagic/issues).**

License
--------

    Copyright 2016 Siim Kinks

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.



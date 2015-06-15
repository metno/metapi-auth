Oauth2 support for metapi
=========================

Provides oauth2 Client Credentials flow for metapi


Testing
-------

When not in production mode you can use the following hardcoded client_id for authentication:

  cafebabe-feed-d00d-badd-5eaf00d5a1ad

Add the following lines to your development.conf:

  # Set to developer mode
  application.mode=DEV

Replace DEV with TEST for integration testing

Using an on-disk database
-------------------------

By default, this installation uses an in-memory database. For
production and sometimes testing this is not really useful. In order
to use a postgresql database create a file, conf/auth.conf, and
populate it with something like this:

db.authorization.driver=org.postgresql.Driver
db.authorization.url="jdbc:postgresql:bora"
db.authorization.user=bora
db.authorization.password="password"

Also make sure to create an empty database with the correct name
before running the application.

JDBC driver for postgresql is included in dependencies, so you should
not have to install that manually.

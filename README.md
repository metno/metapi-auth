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
production and sometimes testing this is not really useful.

In order to use a postgresql database for development, create a
file, conf/development.conf, and add lines like these:

  db.authorization.driver = org.postgresql.Driver
  db.authorization.url = "jdbc:postgresql://localhost:5432/authorization"
  db.authorization.user = metapi
  db.authorization.password = "password"

Then create an empty database using createdb:

  $ createdb authorization

And create a user 'metapi' with a password of your choice for the metapi.
  
  $ createuser metapi -P

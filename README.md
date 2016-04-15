[![Build Status](https://semaphoreci.com/api/v1/projects/48e312f7-fa60-40ce-8b91-cbfbab2345d1/458958/badge.svg)](https://semaphoreci.com/arturopala/play-2-4-crud-with-reactive-mongo)      

#Seed for Play Framework 2.4 application with ReactiveMongo and Macwire

This is Lightbend Activator template, see details here: <https://www.lightbend.com/activator/template/play-2.4-crud-with-reactive-mongo>

[![Deploy](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy)

## Idea

A [**Play Framework 2.4**](https://www.playframework.com/documentation/2.4.x/Home) application template preconfigured to use [**ReactiveMongo**](http://reactivemongo.org/) and [**Macwire**](https://github.com/adamw/macwire).

Provides generic **CRUD** implementation of controller and service, together with type-safe mongodb queries builder (Criteria).

**Criteria** allows also advanced local testing of mongodb querries (in-memory) without communicating real mongodb instance.

## Usage

#### Build

```
sbt test:compile
```

#### Run

```
sbt run
```  

<http://localhost:9000/>


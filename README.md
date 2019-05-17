REST API for money transfers between accounts.

## Technical Overview
 - Implemented in Java 9
 - Using akka-actor to ensure thread safety (without need of blocking), responsiveness, resilience and elasticity
 - Using akka-http as HTTP "framework" which is built on top of akka-actor
 - Storing actor refs in-memory
 - Possibility to deploy actors across different JVM instances
 - Possibility to persist actors in reactive NoSQL databases

## REST API

##### Customer
| Method | URI | Description |
| :---: | :---: | :---: |
| GET | /customers/[id] | Retrieve customer details by id |
| POST | /customers | Create customer account |
| DELETE | /customers/[id] | Delete account |
 
##### Transaction
| Method | URI | Description |
| :---: | :---: | :---: |
| GET | /transactions/[id] | Retrieve transaction by id |
| POST | /transactions | To perform the money transfer |
| DELETE | /transactions/[id] | Delete transaction|
 
##### Account
| Method | URI | Description |
| :---: | :---: | :---: |
| GET | /accounts/[accountNumber] | Retrieve account balance |
 
## How to run
To build the project:
```
mvn clean verify
```
To test:
```
mvn test
```
To run:
```
mvn exec:java
```
### Server Address
By default, server runs in localhost:8080

### Notes
Please change `server.address` property in `application.properties` file to bootstrap the application on the different port if the default one is occupied.
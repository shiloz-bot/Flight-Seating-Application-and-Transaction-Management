<!-- PROJECT LOGO -->
<p align="center">
  <br>
  <a href="https://github.com/shiloz-bot/Flight-Seating-Application-and-Transaction-Management/">
    <img src="image/logo2.png" alt="Logo" width="200" height="120">
  </a>

  <h3 align="center">Flight Seating Application</h3>

  <p align="center">
   :yellow_heart:To gain experience about database and Java JDBC:yellow_heart:
  <br>
  <br>
  </p>
</p>

## :airplane:  About the Program
In this flight seating application, it allows customers to use a CLI to search, book, cancel, etc. flights.
Azure Mysql database is connected to save the customers and the flight they took.

## :hammer:  Tools Used in the Program
* SQL Server through SQL Azure
* Mavan
* Java JDBC
* Git

## Data Model
* Users: A user has a username(case insensitive), password(case sensitive), and balance in their account.
* Reservations: A booking for an itinerary, which may consist of one (direct) or two (one-hop) flights. Each reservation can either be paid or unpaid, cancelled or not, and has a unique ID.
* Itineraries: An itinerary is either a direct flight (consisting of one flight: origin --> destination) or
a one-hop flight (consisting of two flights: origin --> stopover city, stopover city --> destination). Itineraries are returned by the search command.
<br>
<img src="image/UML.png">

## Transaction Management
Used SQL Transaction to gurentee ACID properties:
* 1
* 2
* 3







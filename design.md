Please describe and draw your database design using UML. This will help us understand your implementation and your thinking. 
Explain your design choices in creating new tables. 
Also, describe your thought process in deciding what needs to be persisted on the database and 
what can be implemented in-memory (not persisted on the database). 
Please be concise in your writeup (~ half a page + the UML specification).

We create three tables in our Flights App. They are used to memorize the information of all customers. In other words, information stored in database can be access from different terminal.

Users table takes username as a Primary Key and records the salted username, its corresponding hashed password, and the user’s balance. We store users’ log in information in our database is because log in the user is the very first step for each method to be implemented in our Flight App. A user’s balance needs to be track in the database, since actions from different terminal while log in as the same user will affect the user’s balance dependently. Our Flight App always need to access the up-to-date user balance to return results when users decide to pay or to cancel their reservations. 

Reservations table takes reservation ID as a Primary Key. It gets two foreign keys, where “uname” references the username in Users table and “fid1” references the flight ID in the Flights table. If the itinerary is a direct flight reservation, then we record the flight ID of the direct flight and set the indirect flight ID to -1; else the itinerary is an indirect flight reservation, then we record the flight ID of both flights. We also stores … attributes in our Reservations table in order to retrieve the information more easily in transaction_reservation(). Paid_status to determine whether to refund the customer if the reservation is canceled and get reservation information, check unpaid reservation when a customer want to pay for the reservation. Recording total price of each itinerary is easy for us to deduct or add money to the user’ account. Including whether the reservation has been canceled implies whether we should take the reservation into full consideration. Flight date is recorded to restrict the user to book two flights on the same day.

Store_rid table is used for recording current reservation ID number. It does not have a primary key. 

Details of itineraries from each search can be stored in the data structure, because it’s the search for each user in each terminal. This data should be record temporarily.


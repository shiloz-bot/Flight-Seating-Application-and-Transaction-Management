create table Users(
  uname varchar(255) primary key,
  salt varbinary(20),
  hashCode varbinary(20),
  balance int
  );

create table Reservations(
  rid int primary key,
  uname varchar(255) REFERENCES Users,
  fid1 int REFERENCES Flights,
  fid2 int,
  stopover int,
  paid_status int,
  total_price int,
  canceled int,
  flight_date int
  );

create table Store_Rid(
  rid int
  );

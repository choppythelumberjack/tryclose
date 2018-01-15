create table Person (
  id int primary key auto_increment,
  firstName varchar(255),
  lastName varchar(255),
  age int
);

insert into Person values (default, 'Joe', 'Bloggs', 22);
insert into Person values (default, 'Jack', 'Ripper', 33);
insert into Person values (default, 'Jill', 'Brackman', 44);

This project introduces real data integrity.  While we're using xtdb for its read-scale and time navigation, we want to be able to make validation decisions based on references (does this account belong to the customer?)

```
(jdbc/execute! ["insert into customers (id,email,invited) values (?,?,?)" customer-id email true])
```

Pros
* All the benefits of a SQL db (ACID transactions, referential integrity)
* We've still got the read scale of XTDB
* We've still got time-travelling in XTDB

Cons
* We've still not the single writer in XTDB
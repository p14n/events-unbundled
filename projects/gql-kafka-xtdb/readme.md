This project is the first time we're changing the lookup and write functions.  We use the `invite-customer` handler as-is, but now we read from xtdb to vaildate the email (unsafely!) and write the value back to XTDB.
```
(xt/q (xt/db db) {:find '[e] :where [['e :email email]]})
```
```
(xt/submit-tx db [[::xt/put (assoc entity :xt/id id)]])
```
Pros:
* We now have massive read-scale for our external data
* We've solved a common problem in ES systems; xtdb is bitemporal, which means you can read the state of the world at any given time in the past.  When a new service/component is added to the system, it can read the first be event and ask xtdb "what was the state of the world when this event happened" and boostrap its own datastore (instead of running through *all* events from the beginning)

Cons:
* We don't have real data integrity.  We could introduce this with a transaction function, but even then we wouldn't have *referential* data integrity
* XTDB is single writer, and there will be many events in your system.  Every update to 'customers' will be facing this bottleneck.  It may not be an issue; YMMV
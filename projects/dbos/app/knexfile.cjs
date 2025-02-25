const config = {
  client: 'pg',
  connection: {
    host: 'userdb-ae660b85-e98e-426d-bf5e-b541299cccf3.cvc4gmaa6qm9.us-east-1.rds.amazonaws.com',
    port: 5432,
    user: 'dbos_user',
    password: '',
    ssl: { rejectUnauthorized: false },
    database: 'dbos_node_starter',
  },
  migrations: {
    directory: './migrations'
  }
};

/**
 const config = {
  client: 'pg',
  connection: {
    host: '127.0.0.1',
    port: 5432,
    user: 'postgres',
    password: 'dbos',
    ssl: false,
    database: 'dbos_node_starter',
  },
  migrations: {
    directory: './migrations'
  }
};

 */
module.exports = config;
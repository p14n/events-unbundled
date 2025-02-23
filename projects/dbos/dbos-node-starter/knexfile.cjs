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

module.exports = config;
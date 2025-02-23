export async function up(knex) {
  await knex.schema.createTable('customers', table => {
    table.string('cid', 255).primary();
    table.string('email', 255).notNullable();
  });
}

export async function down(knex) {
  await knex.schema.dropTable('customers');
}
/**
 * Fields in `wanted` that are missing from `existing`, by name. Only NULLABLE fields are
 * ever returned for addition: BigQuery can append a nullable column to a live table without
 * touching existing rows, but a REQUIRED column can't be added to a table that has data -
 * so a missing REQUIRED field is a programming error and throws instead.
 */
function missingNullableFields(existing, wanted) {
  const have = new Set(existing.map((f) => f.name));
  const missing = wanted.filter((f) => !have.has(f.name));
  const required = missing.filter((f) => (f.mode || "NULLABLE") !== "NULLABLE");
  if (required.length > 0) {
    throw new Error(`Cannot add REQUIRED field(s) to an existing table: ${required.map((f) => f.name).join(", ")}`);
  }
  return missing;
}

module.exports = { missingNullableFields };

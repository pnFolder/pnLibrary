package org.pnfolder.pnlibrary.currency;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryPage;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType;

/** Builds and runs filtered, paged ledger history queries against {@link JdbcCurrencySchema}. */
final class JdbcHistoryQuery {
    private final String transactionsTable;
    private final JdbcTransactionDecoder decoder;

    JdbcHistoryQuery(@NotNull JdbcCurrencySchema schema, @NotNull JdbcTransactionDecoder decoder) {
        this.transactionsTable = schema.getTransactionsTable();
        this.decoder = decoder;
    }

    /** Returns one page of {@code currency} history matching {@code query}, newest first. */
    @NotNull
    CurrencyHistoryPage page(@NotNull Connection connection, @NotNull CurrencyKey currency, @NotNull CurrencyHistoryQuery query)
            throws SQLException {
        SqlStatement history = statement(currency, query);
        try (PreparedStatement statement = connection.prepareStatement(history.sql)) {
            List<Object> parameters = new ArrayList<Object>(history.parameters);
            parameters.add(query.getLimit() + 1);
            parameters.add(query.getOffset());
            for (int index = 0; index < parameters.size(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            try (ResultSet results = statement.executeQuery()) {
                List<CurrencyTransaction> values = new ArrayList<CurrencyTransaction>();
                while (results.next()) {
                    values.add(decoder.decode(results));
                }
                List<CurrencyTransaction> items = values.subList(0, Math.min(query.getLimit(), values.size()));
                return new CurrencyHistoryPage(new ArrayList<CurrencyTransaction>(items), query.getOffset(),
                        values.size() > query.getLimit());
            }
        }
    }

    /** Translates the filters of {@code query} into a parameterized SELECT without limit and offset values. */
    private SqlStatement statement(CurrencyKey currency, CurrencyHistoryQuery query) {
        List<String> clauses = new ArrayList<String>();
        clauses.add("currency_id = ?");
        List<Object> parameters = new ArrayList<Object>();
        parameters.add(currency.toString());

        if (query.getAccount() != null) {
            clauses.add("(source_account = ? OR target_account = ?)");
            parameters.add(query.getAccount().toString());
            parameters.add(query.getAccount().toString());
        }
        if (query.getActor() != null) {
            clauses.add("actor_type = ? AND actor_id = ?");
            parameters.add(query.getActor().getType().name());
            parameters.add(query.getActor().getId());
        }
        if (query.getService() != null) {
            clauses.add("service_id = ?");
            parameters.add(query.getService());
        }
        if (query.getFrom() != null) {
            clauses.add("created_at >= ?");
            parameters.add(Timestamp.from(query.getFrom()));
        }
        if (query.getUntil() != null) {
            clauses.add("created_at < ?");
            parameters.add(Timestamp.from(query.getUntil()));
        }
        if (!query.getTypes().isEmpty()) {
            StringBuilder marks = new StringBuilder();
            for (CurrencyTransactionType type : query.getTypes()) {
                if (marks.length() > 0) {
                    marks.append(',');
                }
                marks.append('?');
                parameters.add(type.name());
            }
            clauses.add("transaction_type IN (" + marks + ")");
        }
        return new SqlStatement("SELECT * FROM " + transactionsTable + " WHERE " + String.join(" AND ", clauses)
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?", parameters);
    }

    /** SQL text with its positional parameters. */
    private static final class SqlStatement {
        final String sql;
        final List<Object> parameters;

        SqlStatement(String sql, List<Object> parameters) {
            this.sql = sql;
            this.parameters = parameters;
        }
    }
}

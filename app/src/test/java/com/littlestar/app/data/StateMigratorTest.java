package com.littlestar.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.littlestar.app.data.store.StateMigrator;

import org.junit.Test;

/** v1 → v2 只去掉奖励字段，保留学习记录。 */
public final class StateMigratorTest {

    @Test
    public void version1_removesStars_preservesLearningHistory() {
        JsonObject original = JsonParser.parseString("""
                {"schemaVersion":1,"words":{"mao":{"learnedAt":123,"box":2}},
                 "dailyPlans":{"2026-10-24":{"goal":3,"newWordIds":["mao"]}},
                 "sessions":[{"id":1,"stars":3,"correct":2,"completedAt":456,
                   "answers":[{"wordId":"mao","correct":true,"firstTry":true}]}]}
                """).getAsJsonObject();
        JsonObject expected = original.deepCopy();
        expected.addProperty("schemaVersion", 2);
        expected.getAsJsonArray("sessions").get(0).getAsJsonObject().remove("stars");

        JsonObject migrated = StateMigrator.migrate(original, 1);
        assertEquals(expected, migrated);
        assertFalse(migrated.getAsJsonArray("sessions").get(0).getAsJsonObject().has("stars"));
    }

    @Test
    public void version1_withoutSessions_upgradesSafely() {
        JsonObject migrated = StateMigrator.migrate(new JsonObject(), 1);
        assertEquals(2, migrated.get("schemaVersion").getAsInt());
        assertFalse(migrated.has("sessions"));
    }

    @Test
    public void version2_preservesExistingData() {
        JsonObject original = JsonParser.parseString(
                "{\"schemaVersion\":2,\"nextSessionId\":5,\"favourites\":{\"song\":123}}")
                .getAsJsonObject();
        assertEquals(original.deepCopy(), StateMigrator.migrate(original, 2));
    }
}

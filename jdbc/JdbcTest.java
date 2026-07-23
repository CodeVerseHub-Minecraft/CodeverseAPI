import com.zaxxer.hikari.*;
import net.codeverse.api.identity.*;
import net.codeverse.jdbc.JdbcIdentityService;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class JdbcTest {
    static int fail = 0;
    static void expect(String label, boolean cond) {
        if (!cond) fail++;
        System.out.println((cond ? "OK   " : "FAIL ") + label);
    }

    static UUID uuid(String hex) {
        return UUID.fromString(hex.replaceFirst(
            "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }

    public static void main(String[] a) throws Exception {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/network?useSSL=false&allowPublicKeyRetrieval=true");
        hc.setUsername("network"); hc.setPassword("testpw");
        hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hc.setMaximumPoolSize(4);
        HikariDataSource ds = new HikariDataSource(hc);

        ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
        JdbcIdentityService svc = new JdbcIdentityService(ds, exec, "codeverse_accounts", Duration.ofMinutes(5));

        expect("probe finds the accounts table", svc.probe());
        expect("linkage reported available", svc.isLinkageAvailable());

        UUID java = uuid("11111111111111111111111111111111");
        UUID bedrock = uuid("22222222222222222222222222222222");
        UUID cracked = uuid("33333333333333333333333333333333");
        UUID future = uuid("44444444444444444444444444444444");
        UUID shared = uuid("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        Identity elchi = svc.byMinecraftId(java).join().orElseThrow();
        expect("resolves by minecraft uuid", elchi.username().equals("Elchi"));
        expect("tier parsed", elchi.tier() == TrustTier.PREMIUM);
        expect("totp detected from a non null secret", elchi.totpEnrolled());
        expect("discord link surfaced", elchi.discordId().orElse("").equals("998877"));
        expect("registered timestamp carried", elchi.isRegistered());

        Identity br = svc.byMinecraftId(bedrock).join().orElseThrow();
        expect("bedrock account resolves", br.tier() == TrustTier.BEDROCK);

        // The property the whole internal id design exists for.
        expect("linked java and bedrock share one internal id",
            elchi.internalId().equals(br.internalId()) && elchi.internalId().equals(shared));
        expect("but are different minecraft accounts", !elchi.minecraftId().equals(br.minecraftId()));

        List<Identity> linked = svc.linkedAccounts(shared).join();
        expect("linkedAccounts returns both accounts", linked.size() == 2);
        expect("a lone account yields one entry",
            svc.linkedAccounts(uuid("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).join().size() == 1);

        Identity byName = svc.byUsername("elchi").join().orElseThrow();
        expect("username lookup is case insensitive", byName.minecraftId().equals(java));
        expect("prefixed cracked name resolves",
            svc.byUsername("~Someone").join().orElseThrow().tier() == TrustTier.CRACKED);

        expect("unknown uuid yields empty", svc.byMinecraftId(UUID.randomUUID()).join().isEmpty());
        expect("unknown username yields empty", svc.byUsername("nobody").join().isEmpty());

        expect("discord lookup finds the person", 
            svc.byDiscordId("998877").join().orElseThrow().internalId().equals(shared));
        expect("unknown discord id yields empty", svc.byDiscordId("000").join().isEmpty());

        // An unrecognised tier must fail closed, not throw and not be trusted.
        Identity unknownTier = svc.byMinecraftId(future).join().orElseThrow();
        expect("unrecognised tier degrades to the least trusted", unknownTier.tier() == TrustTier.CRACKED);
        expect("and is therefore barred from elevation", !unknownTier.eligibleForElevatedPermissions());
        expect("zero timestamps mean never", unknownTier.registeredAt().isEmpty());

        // Caching. Both caches are populated by either lookup, so a name
        // lookup warms the uuid cache and the reverse, which is why a scoreboard
        // reading by uuid does not miss after a command resolved by name.
        svc.invalidateAll();
        expect("cached read is empty before any lookup", svc.cachedByMinecraftId(cracked).isEmpty());
        svc.byMinecraftId(cracked).join();
        expect("cached read populated after a lookup", svc.cachedByMinecraftId(cracked).isPresent());

        svc.invalidateAll();
        svc.byUsername("~Someone").join();
        expect("a username lookup also warms the uuid cache", svc.cachedByMinecraftId(cracked).isPresent());

        svc.invalidate(cracked);
        expect("invalidate clears the uuid entry", svc.cachedByMinecraftId(cracked).isEmpty());

        // Preload warms in one statement
        svc.invalidateAll();
        svc.preload(List.of(java, bedrock, cracked, future)).join();
        expect("preload warms every requested account",
            svc.cachedByMinecraftId(java).isPresent() && svc.cachedByMinecraftId(bedrock).isPresent()
            && svc.cachedByMinecraftId(cracked).isPresent() && svc.cachedByMinecraftId(future).isPresent());
        expect("preload of an empty set is harmless", svc.preload(List.of()).join() == null);

        // A cross-check the API's default methods work over this implementation
        expect("isAtLeast satisfied for a premium account",
            svc.isAtLeast(java, TrustTier.BEDROCK).join());
        expect("isAtLeast refused for a cracked account",
            !svc.isAtLeast(cracked, TrustTier.PREMIUM).join());
        expect("isAtLeast on an unknown account is false, never true",
            !svc.isAtLeast(UUID.randomUUID(), TrustTier.CRACKED).join());

        expect("cachedTier reads through the default method",
            svc.cachedTier(java).orElseThrow() == TrustTier.PREMIUM);

        // Failure must be distinguishable from absence.
        ds.close();
        boolean threw = false;
        try {
            svc.byMinecraftId(UUID.randomUUID()).join();
        } catch (CompletionException expected) {
            threw = expected.getCause() instanceof JdbcIdentityService.IdentityLookupException;
        }
        expect("an unreadable database completes exceptionally, not empty", threw);

        System.out.println(fail == 0 ? "\nALL JDBC IDENTITY TESTS PASS" : "\n" + fail + " FAILURES");
        if (fail != 0) System.exit(1);
    }
}

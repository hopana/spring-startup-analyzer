package io.github.linyimin0812.async.executor;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * @author linyimin
 **/
public class NamedThreadFactoryTest {

    @Test
    public void newThread() {
        NamedThreadFactory factory = new NamedThreadFactory("test");
        Thread thread = factory.newThread(() -> {});

        Pattern pattern = Pattern.compile("test-(\\d+)-thread-(\\d+)");
        Matcher matcher = pattern.matcher(thread.getName());

        assertTrue(matcher.matches());
        assertFalse(thread.isDaemon());
    }
}
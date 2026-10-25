package com.lookout.jenkins;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest2;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.tasks.Builder;

/**
 * {@link Builder} that simply counts how many times it was executed.
 *
 * @author Jørgen P. Tjernø <jorgen.tjerno@mylookout.com>
 */
public class CountBuilder extends Builder {
    int count = 0;

    public int getCount() {
        return count;
    }

    public synchronized boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        count++;
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<Builder> {
        @Override
        public Builder newInstance(StaplerRequest2 req, JSONObject data) {
            throw new UnsupportedOperationException();
        }

        public String getDisplayName() {
            return "Count Number Of Builds";
        }
    }
}

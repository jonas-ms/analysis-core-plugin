package io.jenkins.plugins.analysis.core.history;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.NoSuchElementException;

import io.jenkins.plugins.analysis.core.steps.BuildResult;
import io.jenkins.plugins.analysis.core.steps.PipelineResultAction;

import hudson.model.Result;
import hudson.model.Run;

/**
 * Provides a history of build results. A build history start from a baseline and provides access for all previous
 * results of the same type. The results are selected by a specified {@link ResultSelector}.
 *
 * @author Ulli Hafner
 */
// TODO: actually the baseline has not yet a result attached
public class BuildHistory implements RunResultHistory {
    /** The build to start the history from. */
    private final Run<?, ?> baseline;
    private final ResultSelector selector;

    /**
     * Creates a new instance of {@link BuildHistory}.
     *
     * @param baseline
     *            the build to start the history from
     * @param selector
     *            selects the associated action from a build
     */
    public BuildHistory(final Run<?, ?> baseline, final ResultSelector selector) {
        this.baseline = baseline;
        this.selector = selector;
    }

    @Override
    public BuildResult getBaseline() {
        return getResultAction(baseline).getResult();
    }

    /**
     * Returns the previous action.
     *
     * @param ignoreAnalysisResult
     *         if {@code true} then the result of the previous analysis run is ignored when searching for the reference,
     *         otherwise the result of the static analysis reference must be {@link Result#SUCCESS}.
     * @param overallResultMustBeSuccess
     *         if  {@code true} then only runs with an overall result of {@link Result#SUCCESS} are considered as a
     *         reference, otherwise every run that contains results of the same static analysis configuration is
     *         considered
     * @return the previous action
     */
    protected PipelineResultAction getPreviousAction(
            final boolean ignoreAnalysisResult, final boolean overallResultMustBeSuccess) {
        Run<?, ?> run = getPreviousRun(this.baseline, ignoreAnalysisResult, overallResultMustBeSuccess);
        if (run != null) {
            return getResultAction(run);
        }

        return null;
    }

    private Run<?, ?> getPreviousRun(final Run<?, ?> start,
            final boolean ignoreAnalysisResult, final boolean overallResultMustBeSuccess) {
        for (Run<?, ?> run = start.getPreviousBuild(); run != null; run = run.getPreviousBuild()) {
            PipelineResultAction action = getResultAction(run);
            if (hasValidResult(run, action, overallResultMustBeSuccess)
                    && hasSuccessfulAnalysisResult(action, ignoreAnalysisResult)) {
                return run;
            }
        }
        return null;
    }

    private boolean hasSuccessfulAnalysisResult(final PipelineResultAction action, final boolean ignoreAnalysisResult) {
        return action != null && (action.isSuccessful() || ignoreAnalysisResult);
    }

    @CheckForNull
    private PipelineResultAction getResultAction(@Nonnull final Run<?, ?> run) {
        return selector.get(run);
    }

    /**
     * Returns the action of the previous build.
     *
     *
     * @return the action of the previous build, or <code>null</code> if no
     *         such build exists
     */
    @CheckForNull
    protected PipelineResultAction getPreviousAction() {
        return getPreviousAction(false, false);
    }

    protected boolean hasValidResult(final Run<?, ?> run, @CheckForNull final PipelineResultAction action,
            final boolean overallResultMustBeSuccess) {
        Result result = run.getResult();

        if (result == null) {
            return false;
        }
        if (overallResultMustBeSuccess) {
            return result == Result.SUCCESS;
        }
        return result.isBetterThan(Result.FAILURE) || isPluginCauseForFailure(action);
    }

    private boolean isPluginCauseForFailure(@CheckForNull final PipelineResultAction action) {
        if (action == null) {
            return false;
        }
        else {
            return action.getResult().getPluginResult().isWorseOrEqualTo(Result.FAILURE);
        }
    }

    @Override
    public boolean hasPrevious() {
        return getPreviousAction() != null;
    }

    @Override
    public boolean isEmpty() {
        return !hasPrevious();
    }

    @Override
    public BuildResult getPrevious() {
        PipelineResultAction action = getPreviousAction();
        if (action != null) {
            return action.getResult();
        }
        throw new NoSuchElementException("No previous result available");
    }

    @Override
    public Iterator<BuildResult> iterator() {
        return new BuildResultIterator(baseline);
    }

    private class BuildResultIterator implements Iterator<BuildResult> {
        private Run<?, ?> baseline;

        public BuildResultIterator(final Run<?, ?> baseline) {
            this.baseline = baseline;
        }

        @Override
        public boolean hasNext() {
            return baseline != null;
        }

        @Override
        public BuildResult next() {
            PipelineResultAction resultAction = getResultAction(baseline);

            baseline = getPreviousRun(baseline, false, false);

            return resultAction.getResult();
        }

        @Override
        public void remove() {
            // NOP
        }
    }
}

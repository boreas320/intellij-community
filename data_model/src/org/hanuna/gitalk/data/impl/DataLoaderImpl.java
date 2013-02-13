package org.hanuna.gitalk.data.impl;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.data.DataLoader;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.git.reader.CommitParentsReader;
import org.hanuna.gitalk.git.reader.FullLogCommitParentsReader;
import org.hanuna.gitalk.git.reader.util.GitException;
import org.hanuna.gitalk.log.commit.CommitParents;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * @author erokhins
 */
public class DataLoaderImpl implements DataLoader {
    private State state = State.UNINITIALIZED;
    private DataPackImpl dataPack;
    private CommitParentsReader partReader = new CommitParentsReader();
    private List<CommitParents> nextPreLoadPart = null;

    @Override
    public boolean allLogReadied() {
        switch (state) {
            case UNINITIALIZED:
                return false;
            case ALL_LOG_READER:
                return true;
            case PART_LOG_READER:
                return nextPreLoadPart.isEmpty();
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void readAllLog(@NotNull Executor<String> statusUpdater) throws IOException, GitException {
        if (state != State.UNINITIALIZED) {
            throw new IllegalStateException("data was read");
        }
        state = State.ALL_LOG_READER;
        FullLogCommitParentsReader reader = new FullLogCommitParentsReader(statusUpdater);
        List<CommitParents> commitParentsList = reader.readAllCommitParents();
        dataPack = new DataPackImpl(commitParentsList, statusUpdater);
    }

    @Override
    public void readNextPart(@NotNull Executor<String> statusUpdater) throws IOException, GitException {
        switch (state) {
            case ALL_LOG_READER:
                throw new IllegalStateException("data was read");
            case UNINITIALIZED:
                List<CommitParents> commitParentsList = partReader.readNextBlock(statusUpdater);
                nextPreLoadPart = partReader.readNextBlock(statusUpdater);
                state = State.PART_LOG_READER;
                dataPack = new DataPackImpl(commitParentsList, statusUpdater);
                break;
            case PART_LOG_READER:
                if (nextPreLoadPart.isEmpty()) {
                    throw new IllegalStateException("no next commits, but readNextPart was called");
                }
                List<CommitParents> nextPart = partReader.readNextBlock(statusUpdater);
                dataPack.appendCommits(nextPreLoadPart, statusUpdater);
                nextPreLoadPart = nextPart;
                break;
            default:
                throw new IllegalStateException();
        }
    }

    @NotNull
    @Override
    public DataPack getDataPack() {
        return dataPack;
    }

    private static enum State {
        UNINITIALIZED,
        ALL_LOG_READER,
        PART_LOG_READER
    }
}

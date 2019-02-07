/*
 * Copyright 2019 [name of copyright owner]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.storage.kafka.internal;

import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class LuceneStateStore implements StateStore {
    private static final Logger LOG = LoggerFactory.getLogger(LuceneStateStore.class);

    final String name;
    final boolean persistent;

    volatile IndexWriter indexWriter;

    public static LuceneStateStoreBuilder builder(String name) {
        return new LuceneStateStoreBuilder(name);
    }

    LuceneStateStore(LuceneStateStoreBuilder builder) throws IOException {
        this.name = builder.name();
        this.persistent = builder.isPersistent();
        LOG.info("Storing index on path={}", builder.indexDirectory);
        Directory directory = new NIOFSDirectory(Paths.get(builder.indexDirectory));
        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig indexWriterConfigs = new IndexWriterConfig(analyzer);
        indexWriter = new IndexWriter(directory, indexWriterConfigs);
        indexWriter.commit();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void init(ProcessorContext context, StateStore root) {
        try {
            context.register(root, (key, value) -> {
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() {
        try {
            indexWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean persistent() {
        return persistent;
    }

    @Override
    public boolean isOpen() {
        return indexWriter.isOpen();
    }

    public void put(List<Document> value) {
        try {
            LOG.info("Indexing {} documents", value.size());
            for (Document doc : value) {
                indexWriter.addDocument(doc);
            }
            indexWriter.commit();
            LOG.info("{} indexed documents", value.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Directory directory() {
        return indexWriter.getDirectory();
    }

    public static class LuceneStateStoreBuilder implements StoreBuilder<LuceneStateStore> {
        final String name;
        boolean persistent;
        String indexDirectory;

        boolean loggingEnabled;

        public LuceneStateStoreBuilder(String name) {
            this.name = name;
        }

        public LuceneStateStoreBuilder persistent() {
            this.persistent = true;
            return this;
        }

        public LuceneStateStoreBuilder inMemory() {
            this.persistent = false;
            return this;
        }

        public boolean isPersistent() {
            return persistent;
        }

        public LuceneStateStoreBuilder withIndexDirectory(String indexDirectory) {
            this.indexDirectory = indexDirectory;
            return this;
        }

        public String indexDirectory() {
            return indexDirectory;
        }

        @Override
        public LuceneStateStoreBuilder withCachingEnabled() {
            return null;
        }

        @Override
        public LuceneStateStoreBuilder withCachingDisabled() {
            throw new UnsupportedOperationException("caching not supported");
        }

        @Override
        public LuceneStateStoreBuilder withLoggingDisabled() {
            loggingEnabled = false;
            return this;
        }

        @Override
        public LuceneStateStore build() {
            try {
                return new LuceneStateStore(this);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Map<String, String> logConfig() {
            return null;
        }

        @Override
        public boolean loggingEnabled() {
            return loggingEnabled;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public LuceneStateStoreBuilder withLoggingEnabled(Map config) {
            loggingEnabled = true;
            return this;
        }
    }
}

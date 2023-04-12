package io.kestra.core.tasks.storages;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Concat files from the internal storage."
)
@Plugin(
    examples = {
        @Example(
            title = "Concat 2 files with a custom separator",
            code = {
                "files: ",
                "  - \"kestra://long/url/file1.txt\"",
                "  - \"kestra://long/url/file2.txt\"",
                "separator: \"\\n\"",
            }
        ),
        @Example(
            title = "Concat files generated by an each task",
            code = {
                "tasks:",
                "  - id: each",
                "    type: io.kestra.core.tasks.flows.EachSequential",
                "    tasks:",
                "      - id: start_api_call",
                "        type: io.kestra.core.tasks.scripts.Bash",
                "        commands:",
                "          - echo {{ taskrun.value }} > {{ temp.generated }}",
                "        files:",
                "          - generated",
                "    value: '[\"value1\", \"value2\", \"value3\"]'",
                "  - id: concat",
                "    type: io.kestra.core.tasks.storages.Concat",
                "    files:",
                "      - \"{{ outputs.start_api_call.value1.files.generated }}\"",
                "      - \"{{ outputs.start_api_call.value2.files.generated }}\"",
                "      - \"{{ outputs.start_api_call.value3.files.generated }}\"",
            },
            full = true
        ),
        @Example(
            title = "Concat a dynamic number of files",
            code = {
                "tasks:",
                "  - id: echo",
                "    type: io.kestra.core.tasks.scripts.Bash",
                "    commands:",
                "      - echo \"Hello John\" > {{ outputDirs.output }}/1.txt",
                "      - echo \"Hello Jane\" > {{ outputDirs.output }}/2.txt",
                "      - echo \"Hello Doe\" > {{ outputDirs.output }}/3.txt",
                "    outputDirs:",
                "      - output",
                "  - id: concat",
                "    type: io.kestra.core.tasks.storages.Concat",
                "    files: \"{{ outputs.echo.files | jq('.[]') }}\"",
            },
            full = true
        )
    }
)
public class Concat extends Task implements RunnableTask<Concat.Output> {
    @Schema(
        title = "List of files to be concatenated.",
        description = "Must be a `kestra://` storage urls, can be a list of string or json string"
    )
    @PluginProperty(dynamic = true)
    private Object files;

    @Schema(
        title = "The separator to used between files, default is no separator"
    )
    @PluginProperty(dynamic = true)
    private String separator;

    @SuppressWarnings("unchecked")
    @Override
    public Concat.Output run(RunContext runContext) throws Exception {
        File tempFile = runContext.tempFile().toFile();
        try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile)) {
            List<String> finalFiles;
            if (this.files instanceof List) {
                finalFiles = (List<String>) this.files;
            } else if (this.files instanceof String) {
                final TypeReference<List<String>> reference = new TypeReference<>() {};

                finalFiles = JacksonMapper.ofJson(false).readValue(
                    runContext.render((String) this.files),
                    reference
                );
            } else {
                throw new Exception("Invalid `files` properties with type '" + this.files.getClass() + "'");
            }

            finalFiles.forEach(throwConsumer(s -> {
                URI from = new URI(runContext.render(s));
                IOUtils.copyLarge(runContext.uriToInputStream(from), fileOutputStream);

                if (separator != null) {
                    IOUtils.copy(new ByteArrayInputStream(this.separator.getBytes()), fileOutputStream);
                }
            }));
        }

        return Concat.Output.builder()
            .uri(runContext.putTempFile(tempFile))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The concatenate file uri."
        )
        private final URI uri;
    }
}

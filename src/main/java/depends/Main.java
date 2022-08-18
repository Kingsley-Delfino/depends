/*
MIT License

Copyright (c) 2018-2019 Gang ZHANG

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package depends;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.codehaus.plexus.util.StringUtils;

import depends.addons.DV8MappingFileBuilder;
import depends.extractor.AbstractLangProcessor;
import depends.extractor.LangProcessorRegistration;
import multilang.depends.util.file.path.DotPathFilenameWritter;
import multilang.depends.util.file.path.EmptyFilenameWritter;
import multilang.depends.util.file.path.FilenameWritter;
import multilang.depends.util.file.path.UnixPathFilenameWritter;
import multilang.depends.util.file.path.WindowsPathFilenameWritter;
import depends.generator.DependencyGenerator;
import depends.generator.FileDependencyGenerator;
import depends.generator.FunctionDependencyGenerator;
import multilang.depends.util.file.strip.LeadingNameStripper;
import multilang.depends.util.file.FileUtil;
import multilang.depends.util.file.TemporaryFile;
import net.sf.ehcache.CacheManager;
import picocli.CommandLine;
import picocli.CommandLine.PicocliException;

public class Main {

    public static void main(String[] args) {
        try {
            List<String> commands = new ArrayList<>();
            commands.add("-d");
            commands.add("/Users/kingsley/FDSE/GraduationProject");  // 分析结果要存的目录
            commands.add("-f");
            commands.add("plantuml");
            commands.add("java");
            commands.add("/Users/kingsley/FDSE/GraduationProject/CodeSource/Java/commons-io");
            commands.add("test_depends_data");  // 分析结果的文件名
            commands.add("--auto-include");
            args = new String[commands.size()];
            commands.toArray(args);
            LangRegister langRegister = new LangRegister();
            langRegister.register();
            DependsCommand app = CommandLine.populateCommand(new DependsCommand(), args);
            if (app.help) {
                CommandLine.usage(new DependsCommand(), System.out);
                System.exit(0);
            }
            executeCommand(app);
        } catch (Exception e) {
            if (e instanceof PicocliException) {
                CommandLine.usage(new DependsCommand(), System.out);
            } else if (e instanceof ParameterException) {
                System.err.println(e.getMessage());
            } else {
                System.err.println("Exception encountered. If it is a design error, please report issue to us.");
                e.printStackTrace();
            }
            System.exit(0);
        }
    }

    private static void executeCommand(DependsCommand app) throws ParameterException {
        String lang = app.getLang();
        String inputDir = app.getSrc();
        String[] includeDir = app.getIncludes();
        String outputDir = app.getOutputDir();
        inputDir = FileUtil.uniqFilePath(inputDir);
        AbstractLangProcessor langProcessor = LangProcessorRegistration.getRegistry().getProcessorOf(lang);
        if (langProcessor == null) {
            System.err.println("Not support this language: " + lang);
            return;
        }
        if (app.isDv8map()) {
            DV8MappingFileBuilder dv8MapFileBuilder = new DV8MappingFileBuilder(langProcessor.supportedRelations());
            dv8MapFileBuilder.create(outputDir + File.separator + "depends-dv8map.mapping");
        }
        long startTime = System.currentTimeMillis();
        FilenameWritter filenameWritter = new EmptyFilenameWritter();
        if (!StringUtils.isEmpty(app.getNamePathPattern())) {
            switch (app.getNamePathPattern()) {
                case "dot":
                case ".":
                    filenameWritter = new DotPathFilenameWritter();
                    break;
                case "unix":
                case "/":
                    filenameWritter = new UnixPathFilenameWritter();
                    break;
                case "windows":
                case "\\":
                    filenameWritter = new WindowsPathFilenameWritter();
                    break;
                default:
                    throw new ParameterException("Unknown name pattern parameter:" + app.getNamePathPattern());
            }
        }
        // by default use file dependency generator
        DependencyGenerator dependencyGenerator = new FileDependencyGenerator();
        if (!StringUtils.isEmpty(app.getGranularity())) {
            // method parameter means use method generator
            if (app.getGranularity().equals("method")) {
                dependencyGenerator = new FunctionDependencyGenerator();
            } else if (!app.getGranularity().equals("file")) {
                throw new ParameterException("Unknown granularity parameter:" + app.getGranularity());
            }
        }
        if (app.isStripLeadingPath() ||
                app.getStrippedPaths().length > 0) {
            dependencyGenerator.setLeadingStripper(new LeadingNameStripper(app.isStripLeadingPath(), inputDir, app.getStrippedPaths()));
        }
        if (app.isDetail()) {
            dependencyGenerator.setGenerateDetail(true);
        }
        dependencyGenerator.setFilenameRewritter(filenameWritter);
        langProcessor.initial(inputDir, new ArrayList<>(Arrays.asList(includeDir)), new ArrayList<>(), app.getLang().equals("cpp"), app.isOutputExternalDependencies(), app.isDuckTypingDeduce());
        langProcessor.buildDependencies();
        long endTime = System.currentTimeMillis();
        TemporaryFile.getInstance().delete();
        CacheManager.create().shutdown();
        System.out.println("Consumed time: " + (float) ((endTime - startTime) / 1000.00) + " s,  or " + (float) ((endTime - startTime) / 60000.00) + " min.");
    }
}

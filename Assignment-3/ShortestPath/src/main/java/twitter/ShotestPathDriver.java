package twitter;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.Random;
import java.util.stream.IntStream;


public class ShotestPathDriver extends Configured implements Tool {

    // Declaring the logger for the program
    private static final Logger logger = LogManager.getLogger(ShotestPathDriver.class);

    public static void main(final String[] args) {
        // Checking whether 4 arguments are passed or not
        if (args.length != 3) {
            throw new Error("Four arguments required:\n<input-adjcency-dir> <output-path> k");
        }

        try {
            // Running implementation class
            ToolRunner.run(new ShotestPathDriver(), args);
        } catch (final Exception e) {
            logger.error("MR Job Exception", e);
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        final Configuration conf = getConf();

        // Setting the source
        IntStream limit = IntStream.generate(() -> new Random().nextInt(11316811) + 1).limit(Integer.parseInt(args[2]));
        String s = limit.collect(
                StringBuilder::new,
                (sb, i) -> sb.append(i + ","),
                StringBuilder::append
        ).toString();
        logger.info("K Sources:" + s);
//        conf.set("sources", s.substring(0, s.length() - 1));
        conf.set("sources", "1");

        int iterationCounter = 1;
        long currentUpdatedNodes = 1;
        int completed = 0;
        final FileSystem fileSystem = FileSystem.get(conf);
        while (currentUpdatedNodes > 0) {
            // Setting Iteration in context
            conf.setInt("counter", iterationCounter);
            //
            logger.info("Iteration Counter: " + iterationCounter);
            String inputPath, outputPath;
            if (iterationCounter == 1) {
                inputPath = args[0];
            } else {
                inputPath = args[1] + "-" + (iterationCounter - 1);
            }
            outputPath = args[1] + "-" + iterationCounter;

            // Defining Job name
            final Job job = Job.getInstance(conf, "Twitter Followers : ShortestPathDrive");
            job.setJarByClass(ShotestPathDriver.class);
            final Configuration jobConf = job.getConfiguration();

            // Setting the delimeter for the output
            jobConf.set("mapreduce.output.textoutputformat.separator", "-");


            // Setting the classes

            job.setJarByClass(ShotestPathDriver.class);
            job.setMapperClass(ShortestPathMapper.class);
            job.setReducerClass(ShortestPathReducer.class);
            job.setMapOutputKeyClass(IntWritable.class);
            job.setMapOutputValueClass(JsonWritable.class);
            job.setOutputKeyClass(IntWritable.class);
            job.setOutputValueClass(Text.class);


            FileInputFormat.addInputPath(job, new Path(inputPath));
            FileOutputFormat.setOutputPath(job, new Path(outputPath));
            completed = job.waitForCompletion(true) ? 0 : 1;

            Counter currentUpdatedCount = job.getCounters().findCounter(Convergence.numUpdated);

            currentUpdatedNodes = currentUpdatedCount.getValue();
//            if (iterationCounter > 1 && fileSystem.exists(new Path(inputPath))) {
//                fileSystem.delete(new Path(inputPath), true);
//            }
            logger.info("Diameter: " + iterationCounter);

            iterationCounter += 1;
        }
        return completed;
    }

    enum Convergence {
        numUpdated
    }

    enum Diameter {
        path
    }
}

package twitter;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public class KMeansMapper extends Mapper<Object, Text, IntWritable, Text> {
    private final IntWritable centroidId = new IntWritable();
    private final Text textNode = new Text();

    private Map<Integer, Double> centroidList = new LinkedHashMap<>();

    @Override
    public void setup(Context context) throws IOException,
            InterruptedException {
        Configuration configuration = context.getConfiguration();
        int currentCounter = configuration.getInt("counter", -1);
        Integer K = configuration.getInt("K", -1);
        // Intial centroids from the context
        if (currentCounter == 1) {
            String intialCentroids = configuration.get("initial-centroids");
            String[] split = intialCentroids.split(",");
            for(int i = 1 ; i <= split.length; i++){
                centroidList.put(i,Double.parseDouble(split[i-1]));
            }

        } else {
            try {
                URI[] files = context.getCacheFiles();

                if (files == null || files.length == 0) {
                    throw new RuntimeException(
                            "User information is not set in DistributedCache");
                }

                // Read all files in the DistributedCache
                for (URI p : files) {
                    FileSystem fs = FileSystem.get(p, context.getConfiguration());

                    BufferedReader rdr = new BufferedReader(
                            new InputStreamReader(fs.open(new Path(p))));

                    String line;
                    // For each record in the user file
                    while ((line = rdr.readLine()) != null) {

                        String[] split = line.split(",");

                        if (split.length != 0) {
                            // Map the user cluster ID to the centroid coordinate
                            centroidList.put(Integer.parseInt(split[0]), Double.parseDouble(split[1]));
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }


    @Override
    public void map(final Object key, final Text value, final Context context) throws IOException, InterruptedException {

        Configuration configuration = context.getConfiguration();
//        int currentCounter = configuration.getInt("counter", -1);
        int K = configuration.getInt("K", -1);


        // Parsing on comma
        final String[] row = value.toString().split(",");
        Integer userID = Integer.parseInt(row[0]);
        Double followerCount = Double.parseDouble(row[1]);
        int minClusterId = 1;

        Double closestCenter = centroidList.get(minClusterId);
        double minDist = Math.abs(closestCenter - followerCount);
        // Finding the closest center for the given data point
        for (int i = 1; i <= K; i++) {
            if (Math.abs(centroidList.get(i) - followerCount) < minDist) {
                closestCenter = centroidList.get(i);
                minDist = Math.abs(centroidList.get(i) - followerCount);
                minClusterId = i;
            }
        }

        centroidId.set(minClusterId);
        textNode.set(value.toString() + "," + closestCenter);
        context.write(centroidId, textNode);


    }

    @Override
    public void cleanup(Context context) throws IOException,
            InterruptedException {
        final IntWritable centroidcleanup = new IntWritable();
        final Text dummyText = new Text();

        // Add dummy values for handling cases like loss of cluster or duplicates (in next iteration)
        centroidList.forEach((id, c) -> {
            centroidcleanup.set(id);
            dummyText.set("DUM," + c);
            try {
                context.write(centroidcleanup, dummyText);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }

        });


    }


}
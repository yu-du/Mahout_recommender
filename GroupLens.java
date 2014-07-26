package MahoutExperiments.recommender;

import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.common.Weighting;
import org.apache.mahout.cf.taste.eval.IRStatistics;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.eval.RecommenderIRStatsEvaluator;
import org.apache.mahout.cf.taste.impl.eval.GenericRecommenderIRStatsEvaluator;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.example.grouplens.GroupLensDataModel;
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.GenericItemSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.UncenteredCosineSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.apache.mahout.common.RandomUtils;
import java.io.File;
import java.util.Scanner;


class GroupLens_IREvaluator {

  private GroupLens_IREvaluator() {
  }

  public static void main(String[] args) throws Exception {
	    RandomUtils.useTestSeed();    

	DataModel model =null;
	if (args[0]=="100K")
		model = new FileDataModel(new File(args[1]));
	else //(args[0]=="1M" || args[0]=="10M")
		model = new GroupLensDataModel(new File(args[1]));	
		
	int at= Integer.parseInt(args[2]);
	System.out.println("Evaluate precision and recall at: "+ at);

    RecommenderBuilder recommenderBuilder = new RecommenderBuilder() {
      @Override
      public Recommender buildRecommender(DataModel model) throws TasteException {
    	//ItemSimilarity similarityMeasure = new UncenteredCosineSimilarity(model,Weighting.WEIGHTED);
    	UncenteredCosineSimilarity similarityMeasure = new UncenteredCosineSimilarity(model,Weighting.WEIGHTED);
    	          
    	ItemSimilarity similarity= new GenericItemSimilarity(similarityMeasure, model); //Precomputation completed here.
    	similarityMeasure.printOverlapCount();
    	return new GenericItemBasedRecommender(model, similarity);
      }
    };
 
    RecommenderIRStatsEvaluator evaluator =new GenericRecommenderIRStatsEvaluator();

    IRStatistics stats = evaluator.evaluate(recommenderBuilder, null, model, null, at,
                                            GenericRecommenderIRStatsEvaluator.CHOOSE_THRESHOLD,1.0);
    
    System.out.println("Precision "+ stats.getPrecision());
    System.out.println("Recall "+ stats.getRecall());
    System.out.println("F1Measure "+ stats.getF1Measure());   
    System.out.println("nDCG "+ stats.getNormalizedDiscountedCumulativeGain());
    
  }
}

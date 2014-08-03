/* 
A revised version of AbstractSimilarity class in Mahout. The original version is hosted here 
http://grepcode.com/file/repo1.maven.org/maven2/org.apache.mahout/mahout-core/0.8/org/apache/mahout/cf/taste/impl/similarity/AbstractSimilarity.java
Code snippets I've inserted are enclosed by comments using // <Yu Du's code> and // </Yu Du's code>.
*/

package org.apache.mahout.cf.taste.impl.similarity;

import java.util.Collection;

/** Abstract superclass encapsulating functionality that is common to most implementations in this package. */
abstract class AbstractSimilarity extends AbstractItemSimilarity implements UserSimilarity {

  private PreferenceInferrer inferrer;
  private PreferenceTransform prefTransform;
  private SimilarityTransform similarityTransform;
  private final boolean weighted;
  private final boolean centerData;
  private int cachedNumItems;
  private int cachedNumUsers;
  private final RefreshHelper refreshHelper;

  // <Yu Du's code>
  private int [] overlapCount=new int[300]; //You can pick a number larger than 300 for a more precise description of distribution.

  public void printOverlapCount() { 
	  for(int i=0;i<300;i++){
		  System.out.println("overlapCount[" + i + "]="+ overlapCount[i]);
	  }
  }
  // </Yu Du's code>

  /**
   * <p>
   * Creates a possibly weighted AbstractSimilarity.
   * </p>
   */
  AbstractSimilarity(final DataModel dataModel, Weighting weighting, boolean centerData) throws TasteException {
    super(dataModel);
    this.weighted = weighting == Weighting.WEIGHTED;
    this.centerData = centerData;
    this.cachedNumItems = dataModel.getNumItems();
    this.cachedNumUsers = dataModel.getNumUsers();
    this.refreshHelper = new RefreshHelper(new Callable<Object>() {
      @Override
      public Object call() throws TasteException {
        cachedNumItems = dataModel.getNumItems();
        cachedNumUsers = dataModel.getNumUsers();
        return null;
      }
    });
  }

  final PreferenceInferrer getPreferenceInferrer() {
    return inferrer;
  }
  
  @Override
  public final void setPreferenceInferrer(PreferenceInferrer inferrer) {
    Preconditions.checkArgument(inferrer != null, "inferrer is null");
    refreshHelper.addDependency(inferrer);
    refreshHelper.removeDependency(this.inferrer);
    this.inferrer = inferrer;
  }
  
  public final PreferenceTransform getPrefTransform() {
    return prefTransform;
  }
  
  public final void setPrefTransform(PreferenceTransform prefTransform) {
    refreshHelper.addDependency(prefTransform);
    refreshHelper.removeDependency(this.prefTransform);
    this.prefTransform = prefTransform;
  }
  
  public final SimilarityTransform getSimilarityTransform() {
    return similarityTransform;
  }
  
  public final void setSimilarityTransform(SimilarityTransform similarityTransform) {
    refreshHelper.addDependency(similarityTransform);
    refreshHelper.removeDependency(this.similarityTransform);
    this.similarityTransform = similarityTransform;
  }
  
  final boolean isWeighted() {
    return weighted;
  }
  
  /**
   * <p>
   * Several subclasses in this package implement this method to actually compute the similarity from figures
   * computed over users or items. Note that the computations in this class "center" the data, such that X and
   * Y's mean are 0.
   * </p>
   * 
   * <p>
   * Note that the sum of all X and Y values must then be 0. This value isn't passed down into the standard
   * similarity computations as a result.
   * </p>
   * 
   * @param n
   *          total number of users or items
   * @param sumXY
   *          sum of product of user/item preference values, over all items/users prefererred by both
   *          users/items
   * @param sumX2
   *          sum of the square of user/item preference values, over the first item/user
   * @param sumY2
   *          sum of the square of the user/item preference values, over the second item/user
   * @param sumXYdiff2
   *          sum of squares of differences in X and Y values
   * @return similarity value between -1.0 and 1.0, inclusive, or {@link Double#NaN} if no similarity can be
   *         computed (e.g. when no items have been rated by both uesrs
   */
  abstract double computeResult(int n, double sumXY, double sumX2, double sumY2, double sumXYdiff2);
  
  @Override
  public double userSimilarity(long userID1, long userID2) throws TasteException {
    DataModel dataModel = getDataModel();
    PreferenceArray xPrefs = dataModel.getPreferencesFromUser(userID1);
    PreferenceArray yPrefs = dataModel.getPreferencesFromUser(userID2);
    int xLength = xPrefs.length();
    int yLength = yPrefs.length();
    
    if (xLength == 0 || yLength == 0) {
      return Double.NaN;
    }
    
    long xIndex = xPrefs.getItemID(0);
    long yIndex = yPrefs.getItemID(0);
    int xPrefIndex = 0;
    int yPrefIndex = 0;
    
    double sumX = 0.0;
    double sumX2 = 0.0;
    double sumY = 0.0;
    double sumY2 = 0.0;
    double sumXY = 0.0;
    double sumXYdiff2 = 0.0;
    int count = 0;
    
    boolean hasInferrer = inferrer != null;
    boolean hasPrefTransform = prefTransform != null;
    
    while (true) {
      int compare = xIndex < yIndex ? -1 : xIndex > yIndex ? 1 : 0;
      if (hasInferrer || compare == 0) {
        double x;
        double y;
        if (xIndex == yIndex) {
          // Both users expressed a preference for the item
          if (hasPrefTransform) {
            x = prefTransform.getTransformedValue(xPrefs.get(xPrefIndex));
            y = prefTransform.getTransformedValue(yPrefs.get(yPrefIndex));
          } else {
            x = xPrefs.getValue(xPrefIndex);
            y = yPrefs.getValue(yPrefIndex);
          }
        } else {
          // Only one user expressed a preference, but infer the other one's preference and tally
          // as if the other user expressed that preference
          if (compare < 0) {
            // X has a value; infer Y's
            x = hasPrefTransform
                ? prefTransform.getTransformedValue(xPrefs.get(xPrefIndex))
                : xPrefs.getValue(xPrefIndex);
            y = inferrer.inferPreference(userID2, xIndex);
          } else {
            // compare > 0
            // Y has a value; infer X's
            x = inferrer.inferPreference(userID1, yIndex);
            y = hasPrefTransform
                ? prefTransform.getTransformedValue(yPrefs.get(yPrefIndex))
                : yPrefs.getValue(yPrefIndex);
          }
        }
        sumXY += x * y;
        sumX += x;
        sumX2 += x * x;
        sumY += y;
        sumY2 += y * y;
        double diff = x - y;
        sumXYdiff2 += diff * diff;
        count++;
      }
      if (compare <= 0) {
        if (++xPrefIndex >= xLength) {
          if (hasInferrer) {
            // Must count other Ys; pretend next X is far away
            if (yIndex == Long.MAX_VALUE) {
              // ... but stop if both are done!
              break;
            }
            xIndex = Long.MAX_VALUE;
          } else {
            break;
          }
        } else {
          xIndex = xPrefs.getItemID(xPrefIndex);
        }
      }
      if (compare >= 0) {
        if (++yPrefIndex >= yLength) {
          if (hasInferrer) {
            // Must count other Xs; pretend next Y is far away            
            if (xIndex == Long.MAX_VALUE) {
              // ... but stop if both are done!
              break;
            }
            yIndex = Long.MAX_VALUE;
          } else {
            break;
          }
        } else {
          yIndex = yPrefs.getItemID(yPrefIndex);
        }
      }
    }
    
    // "Center" the data. If my math is correct, this'll do it.
    double result;
    if (centerData) {
      double n = count;
      double meanX = sumX / n;
      double meanY = sumY / n;
      // double centeredSumXY = sumXY - meanY * sumX - meanX * sumY + n * meanX * meanY;
      double centeredSumXY = sumXY - meanY * sumX;
      // double centeredSumX2 = sumX2 - 2.0 * meanX * sumX + n * meanX * meanX;
      double centeredSumX2 = sumX2 - meanX * sumX;
      // double centeredSumY2 = sumY2 - 2.0 * meanY * sumY + n * meanY * meanY;
      double centeredSumY2 = sumY2 - meanY * sumY;
      result = computeResult(count, centeredSumXY, centeredSumX2, centeredSumY2, sumXYdiff2);
    } else {
      result = computeResult(count, sumXY, sumX2, sumY2, sumXYdiff2);
    }
    
    if (similarityTransform != null) {
      result = similarityTransform.transformSimilarity(userID1, userID2, result);
    }
    
    if (!Double.isNaN(result)) {
      result = normalizeWeightResult(result, count, cachedNumItems);
    }
    return result;
  }
  
  @Override
  public final double itemSimilarity(long itemID1, long itemID2) throws TasteException { 
    DataModel dataModel = getDataModel();
    PreferenceArray xPrefs = dataModel.getPreferencesForItem(itemID1);
    PreferenceArray yPrefs = dataModel.getPreferencesForItem(itemID2);
    int xLength = xPrefs.length();
    int yLength = yPrefs.length();
    
    if (xLength == 0 || yLength == 0) {
      return Double.NaN;
    }
    
    long xIndex = xPrefs.getUserID(0);
    long yIndex = yPrefs.getUserID(0);
    int xPrefIndex = 0;
    int yPrefIndex = 0;
    
    double sumX = 0.0;
    double sumX2 = 0.0;
    double sumY = 0.0;
    double sumY2 = 0.0;
    double sumXY = 0.0;
    double sumXYdiff2 = 0.0;
    int count = 0;
    
    // No, pref inferrers and transforms don't appy here. I think.
    
    while (true) {
      int compare = xIndex < yIndex ? -1 : xIndex > yIndex ? 1 : 0;
      if (compare == 0) {
        // Both users expressed a preference for the item
        double x = xPrefs.getValue(xPrefIndex);
        double y = yPrefs.getValue(yPrefIndex);
        sumXY += x * y;
        sumX += x;
        sumX2 += x * x;
        sumY += y;
        sumY2 += y * y;
        double diff = x - y;
        sumXYdiff2 += diff * diff;
        count++;
      }
      if (compare <= 0) {
        if (++xPrefIndex == xLength) {
          break;
        }
        xIndex = xPrefs.getUserID(xPrefIndex);
      }
      if (compare >= 0) {
        if (++yPrefIndex == yLength) {
          break;
        }
        yIndex = yPrefs.getUserID(yPrefIndex);
      }
    }

    // <Yu Du's code>
    if (count<300)
    	overlapCount[count]++;
    // </Yu Du's code>

    double result;
    if (centerData) {
      // See comments above on these computations
      double n = (double) count;
      double meanX = sumX / n;
      double meanY = sumY / n;
      // double centeredSumXY = sumXY - meanY * sumX - meanX * sumY + n * meanX * meanY;
      double centeredSumXY = sumXY - meanY * sumX;
      // double centeredSumX2 = sumX2 - 2.0 * meanX * sumX + n * meanX * meanX;
      double centeredSumX2 = sumX2 - meanX * sumX;
      // double centeredSumY2 = sumY2 - 2.0 * meanY * sumY + n * meanY * meanY;
      double centeredSumY2 = sumY2 - meanY * sumY;
      result = computeResult(count, centeredSumXY, centeredSumX2, centeredSumY2, sumXYdiff2);
    } else {
      result = computeResult(count, sumXY, sumX2, sumY2, sumXYdiff2);
    }
    
    if (similarityTransform != null) {
      result = similarityTransform.transformSimilarity(itemID1, itemID2, result);
    }
    
    if (!Double.isNaN(result)) {
      result = normalizeWeightResult(result, count, cachedNumUsers);
    }
    return result;
  }

  @Override
  public double[] itemSimilaritiesitemSimilaritiesitemSimilaritiesitemSimilarities(long itemID1, long[] itemID2s) throws TasteException {
    int length = itemID2s.length;
    double[] result = new double[length];
    for (int i = 0; i < length; i++) {
      result[i] = itemSimilarity(itemID1, itemID2s[i]);
    }
    return result;
  }
  
  final double normalizeWeightResult(double result, int count, int num) {
    if (weighted) {
      double scaleFactor = 1.0 - (double) count / (double) (num + 1);
      if (result < 0.0) {
        result = -1.0 + scaleFactor * (1.0 + result);
      } else {
        result = 1.0 - scaleFactor * (1.0 - result);
      }
    }
    // Make sure the result is not accidentally a little outside [-1.0, 1.0] due to rounding:
    if (result < -1.0) {
      result = -1.0;
    } else if (result > 1.0) {
      result = 1.0;
    }
    return result;
  }
  
  @Override
  public final void refresh(Collection<Refreshable> alreadyRefreshed) {
    super.refresh(alreadyRefreshed);
    refreshHelper.refresh(alreadyRefreshed);
  }
  
  @Override
  public final String toString() {
    return this.getClass().getSimpleName() + "[dataModel:" + getDataModel() + ",inferrer:" + inferrer + ']';
  }
  
}

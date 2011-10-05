package pca_transform;

import pca_transform.Assume;
import pca_transform.covmatrixevd.CovarianceMatrixEVDCalculator;
import pca_transform.covmatrixevd.EVDBased;
import pca_transform.covmatrixevd.EVDResult;
import pca_transform.covmatrixevd.SVDBased;
import Jama.Matrix;

public final class PCA {
	/**
	 * ROTATION: rotate the data matrix to get a diagonal covariance matrix. 
	 * This transformation is sometimes simply called PCA.
	 * WHITENING: rotate and scale the data matrix to get 
	 * the unit covariance matrix
	 */
	public enum TransformationType { ROTATION, WHITENING };
	
	private final Matrix whiteningTransformation;
	private final Matrix pcaRotationTransformation;
	private final Matrix v;
	/** Part of the original SVD vector that is responsible for transforming the
	 * input data into a vector of zeros.*/
	private final Matrix zerosRotationTransformation; 
	private final Matrix d;
	private final double[] means;
	private final double threshold;
	
	/** Create the PCA transformation. Use the popular SVD method for internal
	 * calculations
	 * @param data data matrix used to compute the PCA transformation. 
	 * Rows of the matrix are the instances/samples, columns are dimensions.
	 * */
	public PCA(Matrix data){
		this(data, new SVDBased());
	}
	
	/** Create the PCA transformation
	 * @param data data matrix used to compute the PCA transformation. 
	 * Rows of the matrix are the instances/samples, columns are dimensions.
	 * @param evdCalc method of computing eigenvalue decomposition of data's
	 * covariance matrix
	 */
	public PCA(Matrix data, CovarianceMatrixEVDCalculator evdCalc){
		this.means = getColumnsMeans(data);
		Matrix centeredData = shiftColumns(data, means);
		//debugWrite(centeredData, "centeredData.csv");

		EVDResult evd = evdCalc.run(centeredData);
		EVDWithThreshold evdT = new EVDWithThreshold(evd);
		/** Get only the values of the matrices that correspond to 
		 * standard deviations above the threshold*/
		this.d = evdT.getDAboveThreshold();
		this.v = evdT.getVAboveThreshold();
		this.zerosRotationTransformation = evdT.getVBelowThreshold();
		/** TODO: A 3-sigma-like ad-hoc rule */
		this.threshold = 3*evdT.getThreshold();
		
		//debugWrite(this.evd.v, "eigen-v.csv");
		//debugWrite(this.evd.d, "eigen-d.csv");
		
		Matrix sqrtD = sqrtDiagonalMatrix(d);
		Matrix scaling = inverseDiagonalMatrix(sqrtD);
		//debugWrite(scaling, "scaling.csv");
		this.pcaRotationTransformation = v;
		this.whiteningTransformation = 
			this.pcaRotationTransformation.times(scaling);
	}
	
	/**
	 * @return matrix where eigenvectors are placed in columns
	 */
	public Matrix getEigenvectorsMatrix(){
		return v;
	}
	
	public double getEigenvalue(int dimNo){
		return d.get(dimNo, dimNo);
	}
	
	public int getInputDimsNo(){
		return means.length;
	}
	
	public int getOutputDimsNo(){
		return v.getColumnDimension();
	}
	
	/**
	 * Execute selected transformation on given data
	 * @param data data to transform. Rows of the matrix are the 
	 * instances/samples, columns are dimensions.
	 * @param type transformation to apply
	 * @return transformed data
	 */
	public Matrix transform(Matrix data, TransformationType type){
		Matrix centeredData = shiftColumns(data, means);
		Matrix transformation = getTransformation(type); 
		return centeredData.times(transformation);
	}
	
	/**
	 * Check if given point lies in PCA-generated subspace. 
	 * If it does not, it means that the point doesn't belong 
	 * to the transformation domain i.e. it is an outlier. 
	 * See [blue:p.73:sec.1] for a picture explanation of the method.
	 * @param pt point
	 * @return true iff the point lies on all principal axes
	 */
	public boolean belongsToGeneratedSubspace(Matrix pt){
		Assume.assume(pt.getRowDimension()==1);
		Matrix centeredPt = shiftColumns(pt, means);
		Matrix zerosTransformedPt = centeredPt.times(
				zerosRotationTransformation);
		assert zerosTransformedPt.getRowDimension()==1;
		/** Check if all coordinates of the point were zeroed by the 
		 * transformation */
		for(int c = 0; c < zerosTransformedPt.getColumnDimension(); c++)
			if(Math.abs(zerosTransformedPt.get(0, c)) > threshold) {
				return false;
			}
		return true;
	}
	
	/**
	 * Function for JUnit testing purposes only 
	 * */
	protected static Matrix calculateCovarianceMatrix(Matrix data){
		double[] means = getColumnsMeans(data);
		Matrix centeredData = shiftColumns(data, means);
		return EVDBased.calculateCovarianceMatrixOfCenteredData(
				centeredData);
	}
	
	private Matrix getTransformation(TransformationType type){
		switch(type){
		case ROTATION: return pcaRotationTransformation;
		case WHITENING: return  whiteningTransformation;
		default: throw new RuntimeException("Unknown enum type: "+type);
		}
	}
	
	private static Matrix shiftColumns(Matrix data, double[] shifts){
		Assume.assume(shifts.length==data.getColumnDimension());
		Matrix m = new Matrix(
				data.getRowDimension(), data.getColumnDimension());
		for(int c = 0; c < data.getColumnDimension(); c++)
			for(int r = 0; r < data.getRowDimension(); r++)
				m.set(r, c, data.get(r, c) - shifts[c]);
		return m;		
	}
	
	private static double[] getColumnsMeans(Matrix m){
		double[] means = new double[m.getColumnDimension()];
		for(int c = 0; c < m.getColumnDimension(); c++){
			double sum = 0;
			for(int r = 0; r < m.getRowDimension(); r++)
				sum += m.get(r, c);
			means[c] = sum/m.getRowDimension();
		}
		return means;
	}
	
	private static Matrix sqrtDiagonalMatrix(Matrix m){
		assert m.getRowDimension()==m.getColumnDimension();
		Matrix newM = new Matrix(m.getRowDimension(), m.getRowDimension());
		for(int i = 0; i < m.getRowDimension(); i++)
			newM.set(i, i, Math.sqrt(m.get(i, i)));
		return newM;
	}
	
	private static Matrix inverseDiagonalMatrix(Matrix m){
		assert m.getRowDimension()==m.getColumnDimension();
		Matrix newM = new Matrix(m.getRowDimension(), m.getRowDimension());
		for(int i = 0; i < m.getRowDimension(); i++)
			newM.set(i, i, 1/m.get(i, i));
		return newM;
	}
	
}

/**
 * Version of the eigenvalue decomposition where values of standard deviations
 * (i.e. square roots of the eigenvalues) below a certain threshold are omitted.
 */
class EVDWithThreshold {
	/** Double machine precision in the R environment 
	 * (i.e. in the R environment: `.Machine$double.eps`) */
	public static final double precision = 2.220446e-16;

	private final EVDResult evd;
	private final double threshold;
	
	/**
	 * The tol parameter of the method assumes a default value equal to 
	 * `sqrt(.Machine$double.eps)` from the R environment. In the help page
	 * of the R environment `prcomp` function (see paragraph on `tol` 
	 * parameter) it is written that in such setting we will 
	 * "omit essentially constant components". */
	public EVDWithThreshold(EVDResult evd){
		this(evd, Math.sqrt(precision));
	}
	/**
	 * @param tol threshold parameter of the method - the same parameter
	 * as used in R environment's `prcomp` function (see paragraph on `tol` 
	 * parameter). */
	public EVDWithThreshold(EVDResult evd, double tol){
		this.evd = evd;
		this.threshold = firstComponentSD(evd)*tol;
	}

	private static double firstComponentSD(EVDResult evd){
		return Math.sqrt(evd.d.get(0, 0));
	}
	
	public double getThreshold(){
		return threshold;
	}
	
	public Matrix getDAboveThreshold(){
		int aboveThresholdElemsNo = getElementsNoAboveThreshold();
		Matrix newD = evd.d.getMatrix(0, aboveThresholdElemsNo-1, 
				0, aboveThresholdElemsNo-1);
		return newD;
	}
	
	public Matrix getVAboveThreshold(){
		return evd.v.getMatrix(0, evd.v.getRowDimension()-1, 
				0, getElementsNoAboveThreshold()-1);
	}
	
	public Matrix getVBelowThreshold(){
		return evd.v.getMatrix(0, evd.v.getRowDimension()-1,
				getElementsNoAboveThreshold(), evd.v.getColumnDimension()-1);
	}
	
	private int getElementsNoAboveThreshold(){
		for(int i = 0; i < evd.d.getColumnDimension(); i++){
			double val = Math.sqrt(evd.d.get(i, i));
			if(!(val > threshold)) return i;
		}
		return evd.d.getColumnDimension();
	}
}


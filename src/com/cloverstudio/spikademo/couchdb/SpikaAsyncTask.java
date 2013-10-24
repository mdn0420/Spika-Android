package com.cloverstudio.spikademo.couchdb;

import java.io.IOException;

import org.json.JSONException;

import com.cloverstudio.spikademo.R;
import com.cloverstudio.spikademo.SpikaApp;
import com.cloverstudio.spikademo.dialog.HookUpDialog;
import com.cloverstudio.spikademo.dialog.HookUpProgressDialog;
import com.cloverstudio.spikademo.utils.Const;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

public class SpikaAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result>{

	private Command<Result> command;
	private Context context;
	private ResultListener<Result> resultListener;
	private Exception exception;
	private HookUpProgressDialog progressDialog;
	private boolean showProgressBar = false;
	
	public SpikaAsyncTask(Command<Result> command, ResultListener<Result> resultListener, Context context) {
		super();
		this.command = command;
		this.resultListener = resultListener;
		this.context = context;
		
	}
	
	public SpikaAsyncTask(Command<Result> command, ResultListener<Result> resultListener, Context context, boolean showProgressBar) {
		super();
		this.command = command;
		this.resultListener = resultListener;
		this.context = context;
		this.showProgressBar = showProgressBar;
	}
	
	@Override
	protected void onPreExecute() {
		if (SpikaApp.hasNetworkConnection()) {
			super.onPreExecute();
			if (showProgressBar)
			{
				
				progressDialog = new HookUpProgressDialog(context);
				progressDialog.show();
			}
		} else {
			this.cancel(false);
			Log.e(Const.ERROR, Const.OFFLINE);
			final HookUpDialog dialog = new HookUpDialog(context);
			dialog.showOnlyOK(context.getString(R.string.no_internet_connection));
		}
	}

	@Override
	protected Result doInBackground(Params... params) {
		Result result = null;
		try {
			result = (Result) command.execute();
		} catch (JSONException e) {
			exception = e;
			e.printStackTrace();
		} catch (IOException e) {
			exception = e;
			e.printStackTrace();
		}
		return result;
	}

	@Override
	protected void onPostExecute(Result result) {
		super.onPostExecute(result);
		
		if (showProgressBar)
		{
			if (progressDialog.isShowing()) progressDialog.dismiss();
		}
		
		if (exception != null)
		{
			Log.e(Const.ERROR, exception.getMessage());
			
			final HookUpDialog dialog = new HookUpDialog(context);
			String errorMessage = null;
			if (exception == null){
				errorMessage = context.getString(R.string.no_valid_email_password, exception.getMessage());
			}else if (exception instanceof IOException){
			    errorMessage = context.getString(R.string.can_not_connect_to_server, exception.getMessage());
			}else if(exception instanceof JSONException){
			    errorMessage = context.getString(R.string.an_internal_error_has_occurred, exception.getClass().getName() + " " + exception.getMessage());
			}else{
			    errorMessage = context.getString(R.string.an_internal_error_has_occurred, exception.getClass().getName() + " " + exception.getMessage());
			}
						
			if (((Activity)context).hasWindowFocus()) dialog.showOnlyOK(errorMessage);
			if (resultListener != null) resultListener.onResultsFail();
		}
		else
		{
			Log.e(Const.OK, result.toString());
			if (resultListener != null) resultListener.onResultsSucceded(result);
		}
	}

	
	
	
}
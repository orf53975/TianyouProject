package com.tianyou.sdk.holder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.Gson;
import com.paypal.android.sdk.payments.PayPalPayment;
import com.paypal.android.sdk.payments.PaymentActivity;
import com.tianyou.sdk.bean.CreateOrder;
import com.tianyou.sdk.bean.CreateOrder.ResultBean;
import com.tianyou.sdk.bean.CreateOrder.ResultBean.OrderinfoBean;
import com.tianyou.sdk.bean.PayParamInfo;
import com.tianyou.sdk.utils.AppUtils;
import com.tianyou.sdk.utils.AppUtils.DialogCallback;
import com.tianyou.sdk.utils.HttpUtils;
import com.tianyou.sdk.utils.HttpUtils.HttpCallback;
import com.tianyou.sdk.utils.HttpUtils.HttpsCallback;
import com.tianyou.sdk.utils.LogUtils;
import com.tianyou.sdk.utils.ToastUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Handler;

/**
 * 支付逻辑处理
 * @author itstrong
 *
 */
public class PayHandler {

	public enum PayType {
		WECHAT, ALIPAY, QQPAY, UNION, REMIT, WALLET, WXSCAN, GOOGLE, PAYPAL;
	}
    
    private static PayHandler mPaymentHandler;
    private static Activity mActivity;
    private static Handler mHandler;
    
    public PayType mPayType;			//当前支付类型
    public PayParamInfo mPayInfo;		//支付参数集合
    public boolean PAY_FLAG;			//防止多次点击充值
    public boolean mIsShowChoose;		//是否有选择金额页面
    
    private PayHandler() {}
    
    public static PayHandler getInstance(Activity activity, Handler handler) {
    	mHandler = handler;
		mActivity = activity;
		if (mPaymentHandler == null) {
			mPaymentHandler = new PayHandler();
		}
		return mPaymentHandler;
	}
    
    // 创建订单
    public void createOrder() {
    	LogUtils.d("mPayInfo:" + mPayInfo);
    	getPayWayName();
    	if (ConfigHolder.isUnion) {
			doCreateUnionOrder();
		} else {
			createOrder();
		}
    }
    
    private void doCreateUnionOrder() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("appid", ConfigHolder.gameId);
        map.put("token", ConfigHolder.gameToken);
        map.put("userid", ConfigHolder.userId);
        map.put("serverid", mPayInfo.getServerId());
        map.put("servername", mPayInfo.getServerName());
        map.put("roleid", mPayInfo.getRoleId());
        map.put("productid", mPayInfo.getProductId());
        map.put("productname", mPayInfo.getProductName());
        map.put("money", mPayInfo.getMoney());
        map.put("way", mPayWayCode);
        map.put("custominfo", mPayInfo.getCustomInfo());
        map.put("sign", AppUtils.MD5(ConfigHolder.gameId + ConfigHolder.gameToken + ConfigHolder.userId + 
        		mPayInfo.getServerId() + mPayInfo.getRoleId() + mPayInfo.getMoney() + mPayInfo.getProductId()));
        map.put("signtype", "md5");
        HttpUtils.post(mActivity, URLHolder.URL_UNION_CREATE_ORDER, map, new HttpCallback() {
            @Override
            public void onSuccess(String response) {
                CreateOrder createOrder = new Gson().fromJson(response, CreateOrder.class);
                ResultBean result = createOrder.getResult();
                if (result.getCode() == 200) {
                    OrderinfoBean orderinfo = result.getOrderinfo();
                    mPayInfo.setOrderId(orderinfo.getOrderID());
                    mPayInfo.setProductName(orderinfo.getProduct_name());
                    mPayInfo.setPayMoney(orderinfo.getMoNey());
                    if ("ALIPAY".equals(orderinfo.getWay())) {
                        mPayInfo.setSELLER(result.getPayinfo().getSELLER());
                        mPayInfo.setPARTNER(result.getPayinfo().getPARTNER());
                        mPayInfo.setRSA_PRIVATE(result.getPayinfo().getRSA_PRIVATE());
                        mPayInfo.setRSA_PUBLIC(result.getPayinfo().getRSA_PUBLIC());
                    } else if ("UNPAY".equals(orderinfo.getWay())) {
                        mPayInfo.setTnnumber(result.getPayinfo().getTnnumber());
                    } else if ("WXSCAN".equals(orderinfo.getWay())){
                        mPayInfo.setImgstr(result.getPayinfo().getImgstr());
                        mPayInfo.setQqmember(result.getPayinfo().getQqmember());
                    } else if ("GOOGLEPAY".equals(orderinfo.getWay())) {
                    	LogUtils.d("ggproductid= "+result.getPayinfo().getGGproduct_id());
                        mPayInfo.setGoogleProductID(result.getPayinfo().getGGproduct_id());
                    }
                    doPay();
                } else if (mPayType == PayType.WALLET) {
                    showWalletTip();
                } else {
                    ToastUtils.show(mActivity, result.getMsg());
                    mHandler.sendEmptyMessage(4);
                }
            }
            
            @Override
            public void onFailed() {
            	mHandler.sendEmptyMessage(5);
            }
        });
	}

	// 创建订单
    public void createCommonOrder() {
        LogUtils.d("mPayInfo:" + mPayInfo);
        getPayWayName();
        String userId = ConfigHolder.userId;
        String appID = ConfigHolder.gameId;
        String serverID = mPayInfo.getServerId();
        String createOrderUrl = "";
        Map<String, String> map = new HashMap<String, String>();
        map.put("userId", userId);
        map.put("roleId", mPayInfo.getRoleId());
        map.put("appID", appID);
        map.put("serverID", serverID);
        map.put("customInfo", mPayInfo.getCustomInfo());
        map.put("serverName", mPayInfo.getServerName());
        map.put("moNey", mPayInfo.getMoney());
        map.put("Way", mPayWayCode);
        map.put("productId",mPayInfo.getProductId());
        map.put("sign", AppUtils.MD5(userId + serverID + serverID));
        if (mPayType == PayType.GOOGLE || mPayType == PayType.PAYPAL) {
        	createOrderUrl = URLHolder.URL_CREATE_ORDER_OVERSEAS;
        } else {
        	createOrderUrl = URLHolder.URL_CREATE_ORDER;
        }
        HttpUtils.post(mActivity, createOrderUrl, map, new HttpCallback() {
            @Override
            public void onSuccess(String response) {
                CreateOrder createOrder = new Gson().fromJson(response, CreateOrder.class);
                ResultBean result = createOrder.getResult();
                if (result.getCode() == 200) {
                    OrderinfoBean orderinfo = result.getOrderinfo();
                    mPayInfo.setOrderId(orderinfo.getOrderID());
                    mPayInfo.setProductName(orderinfo.getProduct_name());
                    mPayInfo.setPayMoney(orderinfo.getMoNey());
                    if ("ALIPAY".equals(orderinfo.getWay())) {
                        mPayInfo.setSELLER(result.getPayinfo().getSELLER());
                        mPayInfo.setPARTNER(result.getPayinfo().getPARTNER());
                        mPayInfo.setRSA_PRIVATE(result.getPayinfo().getRSA_PRIVATE());
                        mPayInfo.setRSA_PUBLIC(result.getPayinfo().getRSA_PUBLIC());
                    } else if ("UNPAY".equals(orderinfo.getWay())) {
                        mPayInfo.setTnnumber(result.getPayinfo().getTnnumber());
                    } else if ("WXSCAN".equals(orderinfo.getWay())){
                        mPayInfo.setImgstr(result.getPayinfo().getImgstr());
                        mPayInfo.setQqmember(result.getPayinfo().getQqmember());
                    } else if ("GOOGLEPAY".equals(orderinfo.getWay())) {
                    	LogUtils.d("ggproductid= "+result.getPayinfo().getGGproduct_id());
                        mPayInfo.setGoogleProductID(result.getPayinfo().getGGproduct_id());
                    }
                    doPay();
                } else if (mPayType == PayType.WALLET) {
                    showWalletTip();
                } else {
                    ToastUtils.show(mActivity, result.getMsg());
                    mHandler.sendEmptyMessage(4);
                }
            }
            
            @Override
            public void onFailed() {
            	mHandler.sendEmptyMessage(5);
            }
        });
    }
    
    private void showWalletTip() {
        AlertDialog.Builder builder = new Builder(mActivity);
        builder.setMessage("天游币余额不足，是否给天游币充值？");  
        builder.setTitle("提示");
        builder.setPositiveButton("确认", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int arg1) {
                mHandler.sendEmptyMessage(2);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("取消", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int arg1) {
                PAY_FLAG = false;
                dialog.dismiss();
            }
        });
        builder.create().show();
    }
    
    // 获取金额对应的货币值
    public String getCurrencyValue(String money) {
        return Integer.parseInt(money) * mPayInfo.getScale() + mPayInfo.getCurrency();
    }
    
    // 获取金额对应的货币值
    public String getCurrencyValue(int money) {
        return money * mPayInfo.getScale() + mPayInfo.getCurrency();
    }
    
    private String mPayWayCode = "WXPAY";
    public String mPayWayName = "微信支付";
    
    // 获取支付方式名称
    public void getPayWayName() {
        switch (mPayType) {
        case WECHAT:
        	mPayWayCode = "WXPAY";
        	mPayWayName = "微信支付";
            break;
        case ALIPAY:
        	mPayWayCode = "ALIPAY";
        	mPayWayName = "支付宝";
            break;
        case UNION:
        	mPayWayCode = "UNPAY";
        	mPayWayName = "银联支付";
            break;
        case REMIT:
        	mPayWayCode = "BANK_PAY";
        	mPayWayName = "汇款";
            break;
        case WALLET:
        	mPayWayCode = "QBPAY";
        	mPayWayName = "钱包支付";
            break;
        case QQPAY:
        	mPayWayCode = "HANDQ";
        	mPayWayName = "QQ钱包支付";
            break;
        case WXSCAN:
        	mPayWayCode = "WXSCAN";
        	mPayWayName = "微信扫码支付方式";
            break;
        case GOOGLE:
        	mPayWayCode = "GOOGLEPAY";
        	mPayWayName = "Google Payment";
        	break;
        case PAYPAL:
        	mPayWayCode = "PAYPALPAY";
        	mPayWayName = "Paypal Payment";
        	break;
        }
    }
    
    // 创建钱包订单
    public void createWalletOrder() {
        String userId = ConfigHolder.userId;
        String appID = ConfigHolder.gameId;
        String serverID = mPayInfo.getServerId();
        Map<String, String> map = new HashMap<String, String>();
        map.put("userId", userId);
        map.put("roleId", mPayInfo.getRoleId());
        map.put("appID", appID);
        map.put("serverID", serverID);
        map.put("customInfo", mPayInfo.getCustomInfo());
        map.put("serverName", mPayInfo.getServerName());
        map.put("moNey", mPayInfo.getMoney());
        map.put("Way", mPayWayCode);
        map.put("sign", AppUtils.MD5(userId + serverID + serverID));
        HttpUtils.post(mActivity, URLHolder.URL_PAY_WALLET, map, new HttpCallback() {
            @Override
            public void onSuccess(String response) {
                CreateOrder createOrder = new Gson().fromJson(response, CreateOrder.class);
                ResultBean result = createOrder.getResult();
                if (result.getCode() == 200) {
                    OrderinfoBean orderinfo = result.getOrderinfo();
                    mPayInfo.setOrderId(orderinfo.getOrderID());
                    mPayInfo.setProductName(orderinfo.getProduct_name());
                    mPayInfo.setPayMoney(orderinfo.getMoNey());
                    if ("ALIPAY".equals(orderinfo.getWay())) {
                        mPayInfo.setSELLER(result.getPayinfo().getSELLER());
                        mPayInfo.setPARTNER(result.getPayinfo().getPARTNER());
                        mPayInfo.setRSA_PRIVATE(result.getPayinfo().getRSA_PRIVATE());
                        mPayInfo.setRSA_PUBLIC(result.getPayinfo().getRSA_PUBLIC());
                    } else if ("UNPAY".equals(orderinfo.getWay())) {
                        mPayInfo.setTnnumber(result.getPayinfo().getTnnumber());
                    } else if ("WXSCAN".equals(orderinfo.getWay())){
                        mPayInfo.setImgstr(result.getPayinfo().getImgstr());
                        mPayInfo.setQqmember(result.getPayinfo().getQqmember());
                    }
                    doPay();
                } else {
                    ToastUtils.show(mActivity, result.getMsg());
                    mHandler.sendEmptyMessage(4);
                }
            }
            
            @Override
            public void onFailed() {
            	mHandler.sendEmptyMessage(5);
            }
        });
    }
    
    // 开始支付
    private void doPay() {
        switch (mPayType) {
            case WECHAT:
                new WechatPay(mActivity, true, mPayInfo,this).doWeChatPay();
                break;
            case QQPAY:
                new WechatPay(mActivity, false, mPayInfo,this).doWeChatPay();
                break;
            case ALIPAY:
                new Alipay(mActivity, mHandler, mPayInfo).doAlipay();
                break;
            case UNION:
                new UnionPay(mActivity, mPayInfo.getTnnumber()).doUnionPay();
                break;
            case REMIT:
            	mHandler.sendEmptyMessage(6);
                break;
            case WALLET:
            	mHandler.sendEmptyMessage(3);
                break;
            case WXSCAN:
            	mHandler.sendEmptyMessage(7);
                break;
            case GOOGLE:
            	mHandler.sendEmptyMessage(8);
                break;
            case PAYPAL:
            	PayPalPayment payment = new PayPalPayment(new BigDecimal(mPayInfo.getMoney()), "USD", mPayInfo.getProductName(),
                        PayPalPayment.PAYMENT_INTENT_SALE);
                Intent intent = new Intent(mActivity, PaymentActivity.class);
                intent.putExtra(PaymentActivity.EXTRA_PAYMENT, payment);
                mPaymentHandler.PAY_FLAG = false;
                mActivity.startActivityForResult(intent, 0);
                break;
        }
    }
    
    // 查询订单
    public void doQueryOrder() {
        AppUtils.showProgressDialog(mActivity, "查询订单", "正在查询订单，请稍后...", new DialogCallback() {
            public void onDismiss() {
                if (ConfigHolder.isUnion) {
					doUnionQueryOrder();
				} else {
					doCommonQueryOrder();
				}
            }
        });
    }
    
	private void doUnionQueryOrder() {
		Map<String, String> checkParam = new HashMap<String, String>();
        checkParam.put("orderid", mPayInfo.getOrderId());
        checkParam.put("appid", ConfigHolder.gameId);
        checkParam.put("token", ConfigHolder.gameToken);
        checkParam.put("userid", ConfigHolder.userId);
        checkParam.put("type", "android");
        checkParam.put("imei", AppUtils.getPhoeIMEI(mActivity));
        checkParam.put("sign", AppUtils.MD5(mPayInfo.getOrderId() + 
        		ConfigHolder.gameId + ConfigHolder.gameToken + ConfigHolder.userId));
        checkParam.put("signtype", "md5");
        HttpUtils.post(mActivity, URLHolder.URL_UNION_CREATE_ORDER, checkParam, new HttpsCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject jsonObject = new JSONObject(response);
                    JSONObject result = jsonObject.getJSONObject("result");
                    if ("200".equals(result.getString("code"))) {
                        mHandler.sendEmptyMessage(3);
                    } else {
                        mHandler.sendEmptyMessage(4);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
	}
    
    private void doCommonQueryOrder() {
		Map<String, String> checkParam = new HashMap<String, String>();
        checkParam.put("orderID", mPayInfo.getOrderId());
        checkParam.put("appID",ConfigHolder.gameId);
        checkParam.put("Token",ConfigHolder.gameToken);
        HttpUtils.post(mActivity, URLHolder.URL_QUERY_ORDER, checkParam, new HttpsCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject jsonObject = new JSONObject(response);
                    JSONObject result = jsonObject.getJSONObject("result");
                    if ("200".equals(result.getString("code"))) {
                        mHandler.sendEmptyMessage(3);
                    } else {
                        mHandler.sendEmptyMessage(4);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
	}

    public void receivePayParam(String paramInfo) {
        mPayInfo = new Gson().fromJson(paramInfo, PayParamInfo.class);
        LogUtils.d("支付参数信息：" + mPayInfo);
        LogUtils.d("mPayInfo.getMoney().isEmpty():" + mPayInfo.getMoney().isEmpty());
//		mIsShowChoose = mPayInfo.getMoney().isEmpty();
        String result = payParamInfoCheckout();
        if (!result.equals("OK")) {
            ToastUtils.show(mActivity, result + "不能为空");
        }
    }
    
    // 支付参数校验
    private String payParamInfoCheckout() {
        if (mPayInfo.getRoleId() == null || mPayInfo.getRoleId().isEmpty()) {
            return "roleId";
        } else if (mPayInfo.getServerId() == null || mPayInfo.getServerId().isEmpty()) {
            return "serverId";
        } else if (mPayInfo.getGameName() == null || mPayInfo.getGameName().isEmpty()) {
            return "gameName";
        } else if (mPayInfo.getServerName() == null || mPayInfo.getServerName().isEmpty()) {
            return "serverName";
        } else if (mPayInfo.getSign() == null || mPayInfo.getSign().isEmpty()) {
            return "sign";
        } else if (mPayInfo.getSignType() == null || mPayInfo.getSignType().isEmpty())
            return "signType";
        else if (mPayInfo.getProductId() == null || mPayInfo.getProductId().isEmpty()) {	// customInfo验证
            return "productId";
        }
        return "OK";
    }
}
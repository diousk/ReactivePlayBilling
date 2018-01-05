package org.buffer.android.reactiveplaybilling

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import org.buffer.android.reactiveplaybilling.model.*

class RxBilling constructor(context: Context) : PurchasesUpdatedListener {

    private val mapper = ResponseCodeMapper
    private val publishSubject = PublishSubject.create<List<Purchase>>()
    private var billingClient: BillingClient =
            BillingClient.newBuilder(context).setListener(this).build()

    override fun onPurchasesUpdated(responseCode: Int, purchases: MutableList<Purchase>?) {
        if (responseCode == BillingClient.BillingResponse.OK && purchases != null) {
            publishSubject.onNext(purchases)
        } else {
            publishSubject.onError(PurchasesUpdatedError(mapper.mapBillingResponse(responseCode)))
        }
    }

    fun connect(): Single<ConnectionResult> {
        return Single.create {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(@BillingClient.BillingResponse
                                                    responseCode: Int) {
                    if (responseCode == BillingClient.BillingResponse.OK) {
                        it.onSuccess(ConnectionResult(mapper.mapBillingResponse(responseCode)))
                    } else {
                        it.onError(ConnectionFailure(mapper.mapBillingResponse(responseCode)))
                    }
                }

                override fun onBillingServiceDisconnected() {
                    it.onError(ConnectionFailure())
                }
            })
        }
    }

    fun observePurchaseUpdates(): Observable<List<Purchase>> {
        return publishSubject
    }

    fun queryItemsForPurchase(skuList: List<String>): Observable<List<SkuDetails>> {
        return Observable.create {
            val params = SkuDetailsParams.newBuilder()
            params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP)
            billingClient.querySkuDetailsAsync(params.build()) { responseCode, p1 ->
                if (responseCode == BillingClient.BillingResponse.OK) {
                    it.onNext(p1)
                } else {
                    it.onError(ItemsForPurchaseQueryError(mapper.mapBillingResponse(responseCode)))
                }
            }
        }
    }

    fun querySubscriptionsForPurchase(skuList: List<String>): Observable<List<SkuDetails>> {
        return Observable.create {
            val params = SkuDetailsParams.newBuilder()
            params.setSkusList(skuList).setType(BillingClient.SkuType.SUBS)
            billingClient.querySkuDetailsAsync(params.build()) { responseCode, p1 ->
                if (responseCode == BillingClient.BillingResponse.OK) {
                    it.onNext(p1)
                } else {
                    it.onError(ItemsForSubscriptionQueryError(
                            mapper.mapBillingResponse(responseCode)))
                }
            }
        }
    }

    fun purchaseItem(skuId: String, activity: Activity): Observable<PurchaseResponse> {
        return Observable.create {
            val flowParams = BillingFlowParams.newBuilder()
                    .setSku(skuId)
                    .setType(BillingClient.SkuType.INAPP)
                    .build()
            val responseCode = billingClient.launchBillingFlow(activity, flowParams)
            if (responseCode == BillingClient.BillingResponse.OK) {
                it.onNext(PurchaseResponse(mapper.mapBillingResponse(responseCode)))
            } else {
                it.onError(ItemsForPurchaseQueryError(mapper.mapBillingResponse(responseCode)))
            }
        }
    }

    fun queryPurchaseHistory(): Observable<List<Purchase>> {
        return Observable.create {
            billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP) { responseCode, result ->
                if (responseCode == BillingClient.BillingResponse.OK && result != null) {
                    it.onNext(result)
                } else {
                    it.onError(QueryPurchasesError(mapper.mapBillingResponse(responseCode)))
                }
            }
        }
    }

    fun querySubscriptionHistory(): Observable<List<Purchase>> {
        return Observable.create {
            billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS)
            { responseCode, result ->
                if (responseCode == BillingClient.BillingResponse.OK && result != null) {
                    it.onNext(result)
                } else {
                    it.onError(QuerySubscriptionsError(mapper.mapBillingResponse(responseCode)))
                }

            }
        }
    }

    fun consumeItem(purchaseToken: String): Observable<ConsumptionResponse> {
        return Observable.create {
            billingClient.consumeAsync(purchaseToken) { responseCode, outToken ->
                if (responseCode == BillingClient.BillingResponse.OK) {
                    it.onNext(ConsumptionResponse(mapper.mapBillingResponse(responseCode),
                            outToken))
                } else {
                    it.onError(ConsumptionError(mapper.mapBillingResponse(responseCode)))
                }
            }
        }
    }

    fun purchaseSubscription(skuId: String, activity: Activity): Observable<SubscriptionResponse> {
        return Observable.create {
            val flowParams = BillingFlowParams.newBuilder()
                    .setSku(skuId)
                    .setType(BillingClient.SkuType.SUBS)
                    .build()
            val responseCode = billingClient.launchBillingFlow(activity, flowParams)
            if (responseCode == BillingClient.BillingResponse.OK) {
                it.onNext(SubscriptionResponse(mapper.mapBillingResponse(responseCode)))
            } else {
                it.onError(SubscriptionError(mapper.mapBillingResponse(responseCode)))
            }
        }
    }

}
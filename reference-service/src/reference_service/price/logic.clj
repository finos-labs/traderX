(ns reference-service.price.logic
  (:require
   [clojure.math :as math]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [manifold.stream :as s]
   [medley.core :as medley]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection]
   [next.jdbc.prepare :as p]
   [next.jdbc.sql :as sql])
  (:import
   (com.zaxxer.hikari HikariDataSource)
   (java.time Instant LocalDateTime ZoneId)
   (java.time.format DateTimeFormatter)))

(def insert-prices
  "insert into stock_prices
     (_id, price)
    values
     (?,?)")

(def insert-trade
  "insert into trades
     (_id, security, account_id, price, quantity, side, state, _valid_from)
   values
     (?,?,?,?,?,?,?,?)")

(def insert-position
  "insert into positions
     (_id, account_id, security, quantity, value, trade, calculation, _valid_from)
   values
     (?,?,?,?,?,?,?,?)")

(def select-stock-prices
  "select _id as ticker, price
   from stock_prices
   where _id in ")

(def all-stock-prices
  "select _id as ticker, price
   from stock_prices")

(def account-prices
  "select sp._id as ticker, sp.price
   from stock_prices sp
   join positions p on sp._id = p.security
   where p.account_id = ?")

(def update-prices
  "update stock_prices
   set price = ?
   where _id = ?")

(def select-stocks
  "select _id as ticker
   from stocks")

(def select-points-in-time
  "select distinct (coalesce (_valid_to, timestamp '9999-12-31 23:59:59+00:00')) as point, security, account_id as accountId
   from trades
   for all valid_time
   where account_id=?
   order by point asc")

(def trade-intervals
  "select _valid_to as end, _valid_from as start, security, account_id as accountId, state
   from trades
   for all valid_time
   where account_id=?
   order by start, end nulls last")

(def recent-prices
  (atom {}))

(def price-update-stream
  (atom nil))

(defn populate-prices
  [jdbc-ds]
  (let [prices (sql/query jdbc-ds
                          [all-stock-prices])]
    (if (seq prices)
      (reset! recent-prices
              (medley/index-by :ticker prices))
      (with-open [conn (jdbc/get-connection jdbc-ds {:read-only false})]
        (let [stocks (map :ticker
                          (sql/query conn
                                     [select-stocks]))
              prices (mapv
                      (fn [stock]
                        [stock
                         (rand-int 1000)])
                      stocks)
              cache-prices (map (fn [[ticker price]]
                                  {:ticker ticker
                                   :price price})
                                prices)
              insert-prices-statement (str "insert into stock_prices (_id, price) values "
                                           (str/join "," (repeat (count prices) "(?,?)")))]
          (reset! recent-prices
                  (medley/index-by :ticker cache-prices))
          (sql/query conn ["BEGIN READ WRITE"])
          (jdbc/execute! conn
                         (reduce into
                                 [insert-prices-statement]
                                 prices))
          (sql/query conn ["COMMIT"]))))))

(defn generate-new-price
  "Generates a new price for a stock, within +- 5% of the last price.
   If the new price is zero, it will recursively call itself until a non-zero price is generated."
  [last-price]
  (let [delta (* (if (> 0.5 (rand)) 0.05 -0.05)
                 last-price)
        new-price (int (math/floor
                        (+ last-price (rand-int delta))))]
    (if (zero? new-price)
      (+ 1 (rand-int 10))
      new-price)))

(defn save-prices
  [jdbc-ds prices]
  (with-open [conn (jdbc/get-connection jdbc-ds)
              ps (jdbc/prepare conn
                               [update-prices])]
    (doseq [price prices]
      (p/set-parameters ps [(-> price :price int)
                            (-> price :ticker str)])
      (.addBatch ps))
    (.executeBatch ps)))

(defn get-prices
  [jdbc-ds tickers]
  (let [params (str "(" (str/join ", " (repeat (count tickers) "?")) ")")
        prices (sql/query jdbc-ds
                          (into
                           [(str select-stock-prices params)]
                           tickers))]
    prices))

(defn generate-new-prices
  [jdbc-ds]
  (let [prices (if (seq @recent-prices)
                 (vals @recent-prices)
                 (sql/query jdbc-ds
                            [all-stock-prices]))
        new-prices (map #(update %
                                 :price
                                 generate-new-price)
                        prices)]
    (log/info "Generated new prices")
    (reset! recent-prices
            (medley/index-by :ticker new-prices))
    new-prices))

(def select-position
  "select _id as posid, quantity as oldquantity, value
   from positions
   where account_id = ? and security = ?")

(defn position-for
  [jdbc-ds {:keys [security id accountId quantity unitPrice side]}]
  (let [{:keys [posid
                oldquantity
                value]
         :or {posid (str (java.util.UUID/randomUUID))
              oldquantity 0
              value 0}} (first
                         (sql/query jdbc-ds
                                    [select-position
                                     accountId security]))
        new-quantity (+ oldquantity
                        (if (= side "Buy")
                          quantity
                          (- quantity)))
        new-value (+ value
                     (* unitPrice quantity
                        (if (= side "Sell") 1 -1)))
        calculation (format "(+ %d (* %d %d %d))"
                            value unitPrice quantity
                            (if (= "Sell" side) 1 -1))]
    [posid
     accountId
     security
     new-quantity
     new-value
     id
     calculation]))

(defn save-trade
  [jdbc-ds {:keys [id security accountId
                   unitPrice quantity side
                   created updated]
            :as trade}]
  (log/infof "Saving trade %s" trade)
  (with-open [conn (jdbc/get-connection jdbc-ds)]
    (let [trade-pending (LocalDateTime/ofInstant
                         (Instant/ofEpochMilli created)
                         (ZoneId/of "UTC"))
          trade-settled (LocalDateTime/ofInstant
                         (Instant/ofEpochMilli updated)
                         (ZoneId/of "UTC"))
          position (conj (position-for jdbc-ds trade)
                         trade-settled)]
      (log/infof "Saving trade %s and position %s" (pr-str trade) (pr-str position))
      (sql/query conn ["BEGIN READ WRITE"])
      (jdbc/execute! conn [insert-trade
                           id
                           security
                           (long accountId)
                           (long unitPrice)
                           (long quantity)
                           side
                           "Pending"
                           trade-pending])
      (jdbc/execute! conn ["update trades
                            for portion of valid_time
                            from ? to null
                            set state = ?
                            where _id = ?"
                           trade-settled
                           "Settled"
                           id])
      (jdbc/execute! conn (into
                           [insert-position]
                           position))
      (sql/query conn ["COMMIT"]))))

(defn get-recent-prices
  [stocks]
  (mapv
   #(get @recent-prices %)
   stocks))

(def date-time-formatter
  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss'Z'"))

(defn parse-date-time
  [dt]
  (LocalDateTime/parse dt date-time-formatter))

(defn account-trades
  ([jdbc-ds account-id start end]
   (log/infof "Get account trades start %s end %s" start end)
   (sql/query jdbc-ds
              (if start
                ["select _id as id, security, quantity, side, price, state, _valid_from, _valid_to
                  from trades
                  for valid_time
                  from ? to ?
                  where account_id = ?"
                 (parse-date-time start)
                 (when-not (or (nil? end)
                               (= "null" end))
                   (.plusSeconds (parse-date-time end) 0))
                 account-id]
                ["select _id as id, security, quantity, side, price, state, _valid_from, _valid_to
                  from trades
                  for valid_time all
                  where account_id = ?"
                 account-id]))))

(defn account-positions
  [jdbc-ds account-id start end]
  (log/infof "Get account positions start %s end %s" start end)
  (sql/query jdbc-ds
             (if start
               ["select _id as id, security, trade, value, quantity, calculation, _valid_from, _valid_to
                 from positions
                 for valid_time
                 from ? to ?
                 where account_id = ?"
                (parse-date-time start)
                (when-not (or (nil? end)
                              (= "null" end))
                  (.plusSeconds (parse-date-time end) 0))
                account-id]
               ["select _id as id, security, trade, value, quantity, calculation, _valid_from, _valid_to
                 from positions
                 for valid_time all
                 where account_id = ?"
                account-id])))

(defn start-price-update-stream
  [jdbc-ds price-update-interval-ms]
  (let [price-stream (s/periodically
                      price-update-interval-ms
                      0
                      (fn []
                        (generate-new-prices jdbc-ds)))]
    (when-let [previous @price-update-stream]
      (s/close! previous))
    (reset! price-update-stream price-stream)
    (s/consume (fn [new-prices]
                 (save-prices jdbc-ds new-prices))
               price-stream)))

(defn stop-price-update-stream
  []
  (when-let [previous @price-update-stream]
    (s/close! previous)
    (reset! price-update-stream nil)))

(defn get-recent-prices-for-account
  [jdbc-ds account-id]
  (sql/query jdbc-ds
             [account-prices
              account-id]))

(defn get-trade-points-in-time
  [jdbc-ds account-id]
  (sql/query jdbc-ds
             [select-points-in-time
              account-id]))

(defn get-trade-intervals
  [jdbc-ds account-id]
  (sql/query jdbc-ds
             [trade-intervals
              account-id]))

(comment
  (def jdbc-url (connection/jdbc-url
                 {:dbtype "postgresql"
                  :dbname "traderX"
                  :host "localhost"
                  :port 18099
                  :useSSL false}))
  (def jdbc-ds (connection/->pool HikariDataSource
                                  {:jdbcUrl jdbc-url
                                   :maxLifetime 60000}))
  (map :security (account-positions jdbc-ds 22214 nil nil))
  (def prices (sql/query jdbc-ds
                         ["select * from stock_prices where _id in (?,?)"
                          "AAPL" "IBM"]))
  (get-trade-intervals jdbc-ds 52355)

  (sql/query jdbc-ds [select-points-in-time])
  (jdbc/execute! jdbc-ds
                 ["insert into stock_prices (price,_id) values (?,?),(?,?)"
                  101 "AAPL"
                  201 "IBM"])

  (def pricez (sql/query jdbc-ds
                         ["select _id as stock, price, _valid_from, _system_from from stock_prices where _id = ?
               order by _valid_from desc"
                          "AAPL" #_"IBM"]))

  (LocalDateTime/ofInstant (Instant/ofEpochMilli 6477227720)
                           (ZoneId/of "UTC"))
  (def prices (sql/query jdbc-ds
                         [all-stock-prices]))
  (defn populate-prices
    [jdbc-ds]
    (let []
      (if (seq prices)
        (reset! recent-prices
                (medley/index-by :ticker prices))
        (with-open [conn (jdbc/get-connection jdbc-ds)
                    prices-ps (jdbc/prepare conn
                                            [insert-prices])]
          (let [stocks (map :ticker
                            (sql/query conn
                                       [select-stocks]))
                prices (mapv
                        (fn [stock]
                          [stock
                           (rand-int 1000)])
                        stocks)]
            (reset! recent-prices
                    (medley/index-by :ticker prices))
            (jdbc/execute-batch! prices-ps
                                 prices
                                 {:return-keys false
                                  :return-generated-keys false}))))))
  #_1)

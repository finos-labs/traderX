import { Component, OnInit, TemplateRef } from '@angular/core';
import { Options } from '@angular-slider/ngx-slider';
import { Subject } from 'rxjs';
import { Account } from '../model/account.model';
import { TradeInterval  } from '../model/trade.model';
import { AccountService } from '../service/account.service';
import { Stock } from '../model/symbol.model';
import { SymbolService } from '../service/symbols.service';
import { TradeFeedService } from 'main/app/service/trade-feed.service';
import { PriceService } from '../service/price.service';

@Component({
    selector: 'app-report',
    templateUrl: './report.component.html',
    styleUrls: ['./report.component.scss']
})
export class ReportComponent implements OnInit {

  accounts: Account[] = [];
  accountModel?: Account = undefined;
  stocks: Stock[] = [];
  intervalModel?: TradeInterval = undefined;
  createTicketResponse: any;
  private account = new Subject<Account>();
  points: string[] = [];
  hideSlider = true;
  value: number = 0;
  highValue: number = 1;
  options: Options = {
    floor: 0,
    ceil: 1,
    step: 1,
    showTicks: true,
    showTicksValues: true,
    translate: (index): string => {
      if (index === this.points.length) {
        return 'All';
      } else {
        return `v${index}`;
      }}};

  constructor(private accountService: AccountService,
      private symbolService: SymbolService,
      private priceService: PriceService,
      private tradeFeed: TradeFeedService) { }

  ngOnInit(): void {
    this.accountService.getAccounts().subscribe((accounts) => {
        console.log('ReportComponent init', accounts);
        this.accounts = accounts;
        this.setAccount(this.accounts[5]);
    });
    this.symbolService.getStocks().subscribe((stocks) => this.stocks = stocks);
  }

  onAccountChange(account: Account) {
    console.log('onAccountChange', arguments);
    account && this.setAccount(account);
  }

  getAccountName(item: Account) {
      return item.displayName;
  }

  onSliderChange(event: any) {
    console.log('onSliderChange', event);
    this.value = (event.value > this.points.length) ? this.points.length : event.value;
    this.highValue = event.highValue;
    const start = this.points[this.value];
    const end = event.highValue > this.points.length ? 'null' : this.points[event.highValue];
    const label = `${start} - ${end ? end : '...'}`;
    const accountId = this.accountModel?.id || 52355;
    console.log('onSliderChange', start, end, accountId, label);
    this.intervalModel = {
      start,
      end,
      accountId,
      label
    };
  }

  private setAccount(account: Account) {
    this.accountModel = account;
    this.account.next(account);
    this.tradeFeed.emit('/account', account.id);
    this.priceService.getPointsInTime(account.id).subscribe((points:  string[]) => {
      console.log('Report Comp : Trade points', points);
      this.points = points;
      this.setSliderValues(this.points);
    });
  }

  private setSliderValues(points: String[]) {
    this.options = Object.assign({}, this.options, {ceil: points.length});
    this.hideSlider = false;
  }

}
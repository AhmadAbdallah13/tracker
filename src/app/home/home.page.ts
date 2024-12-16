import { Component } from '@angular/core';
import StatsPlugin from '../statsPlugin/stats';


@Component({
  selector: 'app-home',
  templateUrl: 'home.page.html',
  styleUrls: ['home.page.scss'],
})
export class HomePage {

  constructor() {}

  async requestAccess() {
    const result = await StatsPlugin.requestUsageAccess();
    if (result.granted) {
      alert('Usage access granted');
    } else {
      alert('Redirecting to settings...');
    }
  }

}

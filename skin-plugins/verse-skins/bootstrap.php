<?php

use App\Services\Hook;
use Blessing\Filter;
use Illuminate\Support\Facades\Route;

return function (Filter $filter) {
    Hook::addRoute(function () {
        // 皮肤库接口。设计前提：任何启动器都能接，所以不引入自定义鉴权体系。
        //
        //   GET  /api/verse-skins          公开只读，返回公开皮肤（限流 60 次/分钟/IP）
        //   POST /api/verse-skins/apply    需要该账号的 Yggdrasil accessToken（限流 20 次/分钟/IP）
        //
        // 列表走公开数据不需要 token；写操作一律走 accessToken，且只允许操作 token 归属账号
        // 名下的角色，避免出现"拿有效 token 改别人皮肤"的越权。
        Route::prefix('api/verse-skins')
            ->namespace('VerseSkins\Controllers')
            ->middleware(['api'])
            ->group(function () {
                Route::get('/', 'SkinLibraryController@index')
                    ->middleware('throttle:60,1');
                Route::post('apply', 'SkinLibraryController@apply')
                    ->middleware('throttle:20,1');
            });
    });
};

angular.module('VesselMgmtTool',['uiGmapgoogle-maps'])
.config(function(uiGmapGoogleMapApiProvider) {
    uiGmapGoogleMapApiProvider.configure({
        //    key: 'your api key',
        v: '3.17',
        libraries: 'weather,geometry,visualization'
    });
})
.value('PageState',{listing:[]})
.factory('VesselsService',['$http',function($http){
    return {
        load: function(uuid){
            return $http.get('/vessels/'+uuid);
        },
        create: function(vessel){
            return $http.post('/vessels', vessel);
        },
        update: function(vessel){
            return $http.put('/vessels/'+vessel.uuid, vessel);
        },
        search: function(criteria){
            return $http.get('/vessels?query='+JSON.stringify(criteria), {cache: false});
        },
        delete: function(vessel){
            return $http.delete('/vessels/'+vessel.uuid);
        }
    };
}])
.controller('SearchOrCreateVesselController',['$scope','$filter','VesselsService','PageState',function($scope,$filter,VesselsService,PageState){

    $scope.vessel = {width:10,length:50,draft:10};

    $scope.create = function(){
      if($scope.vesselForm.$valid){
        PageState.busy = "creating";
        PageState.successMessage = undefined;
        var newVesssel = $scope.vessel;
        VesselsService.create(newVesssel).success(function(data, status, headers){
            if(status==201) {
              PageState.successMessage = "Congratulations! Vessel "+newVesssel.name+" has been registered.";
              newVesssel.uuid = headers("Location").split('/').pop()
              PageState.vessel = newVesssel;
              PageState.listing.push(newVesssel);
            }
            PageState.busy = false;
            $scope.vesselForm.$setPristine();
        });
      }
    };

    var Criteria = {
      addPrefix: function(criteria,field,model,form){
        if(form[field] && model[field]) criteria[field] = {"$regex": '^'+model[field]+'.*',"$options":"i"}
      },
      addRegex: function(criteria,field,model,form){
        if(form[field] && model[field]) criteria[field] = {"$regex":'.*?'+model[field]+'.*',"$options":"i"} 
      },
      addRange: function(criteria,field,range,model,form){
        if(form[field] && model[field]) {
          var gt = {}; gt[field] = {"$gt":(model[field]-range/2)};
          var lt = {}; lt[field] = {"$lt":(model[field]+range/2)};
          criteria['$and'] = [gt,lt]
        }
      }
    }

    $scope.search = function(){
      PageState.busy = "searching";
      PageState.vessel = undefined;
      PageState.listing = [];
      var q1 = {}; var q2 = {}; var q3 = {}; var q4 = {};
      Criteria.addRegex(q1,"name",$scope.vessel,$scope.vesselForm)
      Criteria.addRange(q1,"width",2,$scope.vessel,$scope.vesselForm)
      Criteria.addRegex(q2,"name",$scope.vessel,$scope.vesselForm)
      Criteria.addRange(q2,"length",2,$scope.vessel,$scope.vesselForm)
      Criteria.addRegex(q3,"name",$scope.vessel,$scope.vesselForm)
      Criteria.addRange(q3,"draft",2,$scope.vessel,$scope.vesselForm)
      Criteria.addPrefix(q4,"name",$scope.vessel,$scope.vesselForm)
      var criteria = {"$or":[q4,q1,q2,q3]}
      VesselsService.search(criteria).success(function(data, status, headers){
          if(status==200) {
          	if(angular.isArray(data)){
          		if(data.length == 1){
					       PageState.vessel = data[0];
          		} else if(data.length>1){
					       PageState.listing = data;
          		}
          	}
          }
          PageState.busy = false;
          $scope.vesselForm.$setPristine();
      });
    };

    $scope.mousedown = function(){
      PageState.successMessage = undefined;
    }
}])
.controller('ListVesselsController',['$scope','$filter','PageState',function($scope,$filter,PageState){

  $scope.$watch(function(){return PageState.listing;}, function(){
      $scope.vessels = PageState.listing;
  });

  $scope.show = function(vessel){
    PageState.vessel = vessel
  };

}])
.controller('ShowVesselController',['$scope','$filter','VesselsService','PageState',function($scope,$filter,VesselsService,PageState){

  $scope.$watch(function(){return PageState.vessel;}, function(){
      $scope.vessel = PageState.vessel;
  });

  $scope.edit = function(vessel){
    PageState.busy = "loading";
    PageState.successMessage = undefined;
    VesselsService.load(vessel.uuid).success(function(data, status, headers){
        if(status==200) {
          if(data.lastSeenPosition){
            data.lastSeenPosition.date = new Date(data.lastSeenPosition.time)
          }
          PageState.vessel = data;
        }
        PageState.busy = false;
        $scope.editMode = true;
    });
  };

  $scope.cancel = function(){
    $scope.editMode = false;
  };

  $scope.addLastSeenPosition = function(){
    var datetime = new Date();
    datetime.setMilliseconds(0);
    datetime.setSeconds(0);
    var lastSeenPosition = {location:[0,0], time: datetime.getTime(), date: datetime}
    PageState.vessel.lastSeenPosition = lastSeenPosition;
  };

  $scope.update = function(vessel){
    PageState.busy = "updating";
    PageState.successMessage = undefined;
    var updatedVessel = vessel;
    if (updatedVessel.lastSeenPosition && updatedVessel.lastSeenPosition.date){
      updatedVessel.lastSeenPosition.time = updatedVessel.lastSeenPosition.date.getTime();
      updatedVessel.lastSeenPosition.date = undefined;
    }
    VesselsService.update(updatedVessel).success(function(data, status, headers){
        if(status==200) {
          PageState.successMessage = "Congratulations! Vessel "+updatedVessel.name+" has been updated.";
          PageState.vessel = updatedVessel;
          PageState.listing = PageState.listing && PageState.listing.map(function(item){if(item.uuid !== updatedVessel.uuid) {return item;} else {return updatedVessel;}});
        }
        PageState.busy = false;
        $scope.vesselFullForm.$setPristine();
        $scope.editMode = false;
    });
  };

  $scope.delete = function(vessel){
    if(confirm("Are you sure?")){
      PageState.busy = "deleting";
      PageState.successMessage = undefined;
      var vesselToDelete = vessel;
      VesselsService.delete(vesselToDelete).success(function(data, status, headers){
        if(status==200) {
          PageState.vessel = undefined;
          PageState.successMessage = "Congratulations! Vessel "+vesselToDelete.name+" has been removed.";
          PageState.listing = PageState.listing && PageState.listing.filter(function(item){return item.uuid !== vesselToDelete.uuid});
        }
        PageState.busy = false;
    });
    }
  };
  
  $scope.mousedown = function(){
    PageState.successMessage = undefined;
  };

}])
.controller('MessageController',['$scope','$filter','PageState',function($scope,$filter,PageState){
    $scope.$watch(function(){return PageState.successMessage;}, function(){
        $scope.successMessage = PageState.successMessage;
    });

    $scope.mousedown = function(){
      PageState.successMessage = undefined;
    };
}]);